package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.TestFixtures.user
import edu.itmo.ultimatumgame.dto.requests.AuthenticateUserRequest
import edu.itmo.ultimatumgame.dto.requests.CreateUserRequest
import edu.itmo.ultimatumgame.exceptions.UserRoleNotAllowedException
import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.model.User
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.UserLoggedOut
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
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
    fun `quickLogin — находит пользователя и возвращает сгенерированный токен`() {
        val u = user()
        loginTarget = u
        every { userService.getUserDetailService() } returns ::lookupUserById
        every { jwtService.generateToken(u) } returns "tok"

        val resp = service.quickLogin(AuthenticateUserRequest(u.id!!))

        assertEquals("tok", resp.token)
    }

    @Test
    fun `quickRegister — создаёт пользователя_PLAYER и возвращает токен`() {
        val savedUser = user(role = Role.PLAYER)
        val userSlot = slot<User>()
        every { userService.create(capture(userSlot)) } returns savedUser
        every { jwtService.generateToken(savedUser) } returns "new-tok"

        val resp = service.quickRegister(CreateUserRequest(nickname = "Alice", role = Role.PLAYER))

        assertEquals("new-tok", resp.token)
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
    fun `quickRegister — ADMIN разрешён`() {
        val savedUser = user(role = Role.ADMIN)
        every { userService.create(any()) } returns savedUser
        every { jwtService.generateToken(savedUser) } returns "admin-tok"

        val resp = service.quickRegister(CreateUserRequest(nickname = "root", role = Role.ADMIN))
        assertEquals("admin-tok", resp.token)
    }
}
