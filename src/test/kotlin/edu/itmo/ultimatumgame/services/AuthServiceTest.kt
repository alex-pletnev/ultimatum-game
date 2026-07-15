package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.TestFixtures.user
import edu.itmo.ultimatumgame.dto.requests.AuthenticateUserRequest
import edu.itmo.ultimatumgame.dto.requests.CreateUserRequest
import edu.itmo.ultimatumgame.exceptions.InvalidJwtException
import edu.itmo.ultimatumgame.exceptions.UserRoleNotAllowedException
import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.model.User
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.UserLoggedOut
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthServiceTest {

    private val jwtService = mockk<JwtService>()
    private val userService = mockk<UserService>()
    private val tokenRevocationService = mockk<TokenRevocationService>(relaxUnitFun = true)
    private val domainEventLogger = mockk<DomainEventLogger>(relaxUnitFun = true)
    private val service = AuthService(jwtService, userService, tokenRevocationService, domainEventLogger)

    // Держим ссылку на текущего "пользователя" для эмуляции getUserDetailService
    private var loginTarget: User? = null
    private fun lookupUserById(id: UUID): User =
        loginTarget?.takeIf { it.id == id } ?: error("miss")

    @Test
    fun `quickLogin — возвращает access + refresh + expiresIn`() {
        val u = user()
        loginTarget = u
        every { userService.getUserDetailService() } returns ::lookupUserById
        every { jwtService.generateAccessToken(u) } returns "access"
        every { jwtService.generateRefreshToken(u) } returns "refresh"
        every { jwtService.accessTokenTtlSeconds() } returns 900L

        val resp = service.quickLogin(AuthenticateUserRequest(u.id!!))

        assertEquals("access", resp.accessToken)
        assertEquals("refresh", resp.refreshToken)
        assertEquals(900L, resp.expiresIn)
    }

    @Test
    fun `quickRegister — создаёт пользователя_PLAYER и возвращает access+refresh+expiresIn`() {
        val savedUser = user(role = Role.PLAYER)
        val userSlot = slot<User>()
        every { userService.create(capture(userSlot)) } returns savedUser
        every { jwtService.generateAccessToken(savedUser) } returns "new-access"
        every { jwtService.generateRefreshToken(savedUser) } returns "new-refresh"
        every { jwtService.accessTokenTtlSeconds() } returns 900L

        val resp = service.quickRegister(CreateUserRequest(nickname = "Alice", role = Role.PLAYER))

        assertEquals("new-access", resp.accessToken)
        assertEquals("new-refresh", resp.refreshToken)
        assertEquals(900L, resp.expiresIn)
        assertEquals("Alice", userSlot.captured.nickname)
        assertEquals(Role.PLAYER, userSlot.captured.role)
    }

    @Test
    fun `quickRegister — бросает UserRoleNotAllowedException при роли NPC`() {
        assertThrows<UserRoleNotAllowedException> {
            service.quickRegister(CreateUserRequest(nickname = "NPC-guy", role = Role.NPC))
        }
    }

    @Test
    fun `logout — извлекает jti, отзывает его и эмитит UserLoggedOut`() {
        val userId = UUID.randomUUID()
        val jti = UUID.randomUUID()
        every { jwtService.extractUsername("bearer-token") } returns userId.toString()
        every { jwtService.extractJti("bearer-token") } returns jti

        service.logout("bearer-token")

        verify { tokenRevocationService.revoke(jti) }
        verify { domainEventLogger.emit(UserLoggedOut(userId = userId)) }
    }

    @Test
    fun `logout — токен без jti не падает, событие всё равно эмитится`() {
        val userId = UUID.randomUUID()
        every { jwtService.extractUsername("legacy-token") } returns userId.toString()
        every { jwtService.extractJti("legacy-token") } returns null

        service.logout("legacy-token")

        verify(exactly = 0) { tokenRevocationService.revoke(any()) }
        verify { domainEventLogger.emit(UserLoggedOut(userId = userId)) }
    }

    @Test
    fun `refresh — валидный refresh-токен возвращает новый accessToken`() {
        val u = user()
        loginTarget = u
        every { userService.getUserDetailService() } returns ::lookupUserById
        every { jwtService.extractType("refresh-tok") } returns "REFRESH"
        every { jwtService.extractUsername("refresh-tok") } returns u.id.toString()
        every { jwtService.isRefreshTokenValid("refresh-tok", u) } returns true
        every { jwtService.generateAccessToken(u) } returns "new-access"
        every { jwtService.accessTokenTtlSeconds() } returns 900L

        val resp = service.refresh("refresh-tok")

        assertEquals("new-access", resp.accessToken)
        assertEquals(900L, resp.expiresIn)
        // refresh не ротируется в MVP
        assertEquals(null, resp.refreshToken)
    }

    @Test
    fun `refresh — access-токен вместо refresh бросает InvalidJwtException`() {
        every { jwtService.extractType("access-tok") } returns "ACCESS"
        assertThrows<InvalidJwtException> { service.refresh("access-tok") }
    }

    @Test
    fun `refresh — невалидный refresh (истёк или подделка) бросает InvalidJwtException`() {
        val u = user()
        loginTarget = u
        every { userService.getUserDetailService() } returns ::lookupUserById
        every { jwtService.extractType("bad-refresh") } returns "REFRESH"
        every { jwtService.extractUsername("bad-refresh") } returns u.id.toString()
        every { jwtService.isRefreshTokenValid("bad-refresh", u) } returns false

        assertThrows<InvalidJwtException> { service.refresh("bad-refresh") }
    }

    @Test
    fun `refresh — реально подделанный refresh-токен (чужой ключ) не даёт 500 (T-064)`() {
        // Real JwtService с ключом A; атакующий подписывает токен ключом B.
        val signingKeyA = Base64.getEncoder().encodeToString(ByteArray(64) { 1 })
        val realJwt = JwtService(signingKeyA, TokenRevocationService())
        val svc = AuthService(realJwt, userService, tokenRevocationService, domainEventLogger)

        val u = user()
        val otherKey = Keys.hmacShaKeyFor(ByteArray(64) { 9 })
        val forged = Jwts.builder()
            .setSubject(u.id.toString())
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .claim("type", "REFRESH")
            .signWith(otherKey, SignatureAlgorithm.HS256)
            .compact()

        // Оба варианта дают 401 через GlobalExceptionsHandler; любое другое = регрессия (напр. 500).
        val ex = assertThrows<Exception> { svc.refresh(forged) }
        val ok = ex is InvalidJwtException || ex is io.jsonwebtoken.security.SignatureException
        assertEquals(true, ok, "Ожидался InvalidJwt|SignatureException, получен ${ex::class.simpleName}")
    }

    @Test
    fun `quickRegister — ADMIN разрешён`() {
        val savedUser = user(role = Role.ADMIN)
        every { userService.create(any()) } returns savedUser
        every { jwtService.generateAccessToken(savedUser) } returns "admin-access"
        every { jwtService.generateRefreshToken(savedUser) } returns "admin-refresh"
        every { jwtService.accessTokenTtlSeconds() } returns 900L

        val resp = service.quickRegister(CreateUserRequest(nickname = "root", role = Role.ADMIN))
        assertEquals("admin-access", resp.accessToken)
        assertEquals("admin-refresh", resp.refreshToken)
    }
}
