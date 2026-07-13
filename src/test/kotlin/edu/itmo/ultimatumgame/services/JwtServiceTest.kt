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
    private val service = JwtService(signingKey)

    @Test
    fun `generateToken + extractUsername — subject равен user_id`() {
        val u = user()
        val token = service.generateToken(u)
        assertEquals(u.id.toString(), service.extractUsername(token))
    }

    @Test
    fun `isTokenValid — свежий токен для того же пользователя валиден`() {
        val u = user()
        val token = service.generateToken(u)
        assertTrue(service.isTokenValid(token, u))
    }

    @Test
    fun `isTokenValid — токен от чужого username невалиден`() {
        val u1 = user(id = UUID.randomUUID())
        val u2 = user(id = UUID.randomUUID())
        val token = service.generateToken(u1)
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
