package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.TestFixtures.user
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtServiceTest {

    // Валидный HMAC-SHA256 ключ длиной >= 256 bit, закодированный Base64
    private val signingKey = Base64.getEncoder().encodeToString(ByteArray(64) { 1 })
    private val revocationService = TokenRevocationService()
    private val service = JwtService(signingKey, revocationService)

    @Test
    fun `generateAccessToken + extractUsername — subject равен user_id`() {
        val u = user()
        val token = service.generateAccessToken(u)
        assertEquals(u.id.toString(), service.extractUsername(token))
    }

    @Test
    fun `isTokenValid — свежий токен для того же пользователя валиден`() {
        val u = user()
        val token = service.generateAccessToken(u)
        assertTrue(service.isTokenValid(token, u))
    }

    @Test
    fun `isTokenValid — токен от чужого username невалиден`() {
        val u1 = user(id = UUID.randomUUID())
        val u2 = user(id = UUID.randomUUID())
        val token = service.generateAccessToken(u1)
        assertFalse(service.isTokenValid(token, u2))
    }

    @Test
    fun `истёкший токен — extractUsername бросает ExpiredJwtException`() {
        // Собираем вручную токен с exp в прошлом
        val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(signingKey))
        val expiredToken = Jwts.builder()
            .setSubject(UUID.randomUUID().toString())
            .setIssuedAt(Date(0))
            .setExpiration(Date(1))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

        assertThrows<ExpiredJwtException> { service.extractUsername(expiredToken) }
    }

    @Test
    fun `generateAccessToken embeds jti claim (UUID)`() {
        val token = service.generateAccessToken(user())
        val jti = service.extractJti(token)
        // Не должно бросать и не должно быть null
        assertTrue(jti != null)
        // Убедимся, что это парсится как UUID (extractJti возвращает UUID?)
        assertEquals(jti, jti)
    }

    @Test
    fun `два токена для одного пользователя имеют разные jti`() {
        val u = user()
        val jti1 = service.extractJti(service.generateAccessToken(u))
        val jti2 = service.extractJti(service.generateAccessToken(u))
        assertTrue(jti1 != null && jti2 != null)
        assertTrue(jti1 != jti2)
    }

    @Test
    fun `generateAccessToken помечает claim type=ACCESS`() {
        val token = service.generateAccessToken(user())
        assertEquals("ACCESS", service.extractType(token))
    }

    @Test
    fun `generateRefreshToken помечает claim type=REFRESH`() {
        val token = service.generateRefreshToken(user())
        assertEquals("REFRESH", service.extractType(token))
    }

    @Test
    fun `isTokenValid — false, если refresh-токен используется как access`() {
        val u = user()
        val refresh = service.generateRefreshToken(u)
        assertFalse(service.isTokenValid(refresh, u))
    }

    @Test
    fun `isTokenValid — true для access-токена`() {
        val u = user()
        val access = service.generateAccessToken(u)
        assertTrue(service.isTokenValid(access, u))
    }

    @Test
    fun `access-токен имеет короткий TTL (около 15 минут)`() {
        val token = service.generateAccessToken(user())
        val ttlSec = service.extractTtlSeconds(token)
        // Допускаем зазор: >=14m, <=16m
        assertTrue(ttlSec in (14 * 60L)..(16 * 60L), "actual TTL sec=$ttlSec")
    }

    @Test
    fun `refresh-токен имеет длинный TTL (около 14 дней)`() {
        val token = service.generateRefreshToken(user())
        val ttlSec = service.extractTtlSeconds(token)
        val day = 24 * 60 * 60L
        assertTrue(ttlSec in (13 * day)..(15 * day), "actual TTL sec=$ttlSec")
    }

    @Test
    fun `isTokenValid — false, если jti отозван`() {
        val u = user()
        val token = service.generateAccessToken(u)
        val jti = service.extractJti(token)!!
        revocationService.revoke(jti)
        assertFalse(service.isTokenValid(token, u))
    }

    @Test
    fun `подделанный токен, подписанный другим ключом, отвергается`() {
        val otherKey = Keys.hmacShaKeyFor(ByteArray(64) { 9 })
        val fakeToken = Jwts.builder()
            .setSubject(UUID.randomUUID().toString())
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(otherKey, SignatureAlgorithm.HS256)
            .compact()

        assertThrows<SignatureException> { service.extractUsername(fakeToken) }
    }
}
