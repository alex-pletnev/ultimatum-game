package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.model.User
import edu.itmo.ultimatumgame.util.logger
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.security.Key
import java.util.Date
import java.util.HashMap
import java.util.UUID
import java.util.concurrent.TimeUnit

// username is user.id

@Service
class JwtService(
    @Value("\${token.signing.key}")
    private val jwtSigningKey: String,
    private val tokenRevocationService: TokenRevocationService,
) {

    private val logger = logger()

    companion object {
        private const val TOKEN_EXPIRATION_DAYS = 365L
    }

    fun generateToken(userDetails: UserDetails): String {
        logger.info("dto to Map для пользователя с username=${userDetails.username}")
        val claims = HashMap<String, Any>()
        if (userDetails is User) {
            claims["nickname"] = userDetails.nickname
            claims["role"] = userDetails.role
            claims["createdAt"] = userDetails.createdAt
        }
        return generateToken(claims, userDetails)
    }

    fun extractUsername(token: String): String {
        logger.info("Извлечение username из токена")
        return extractClaim(token, Claims::getSubject)
    }

    /**
     * Возвращает `jti` claim токена или `null`, если claim отсутствует / не парсится.
     * Nullable — на случай токенов, выпущенных до релиза jti.
     */
    fun extractJti(token: String): UUID? {
        val raw = runCatching { extractClaim(token, Claims::getId) }.getOrNull() ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val isExpired = isTokenExpired(token)
        val usernameMatches = extractUsername(token) == userDetails.username
        val isRevoked = extractJti(token)?.let(tokenRevocationService::isRevoked) ?: false
        logger.info(
            "Проверка валидности токена: isExpired=$isExpired, usernameMatches=$usernameMatches, isRevoked=$isRevoked"
        )
        return !isExpired && usernameMatches && !isRevoked
    }

    // extraction util
    private fun isTokenExpired(token: String): Boolean {
        val expired = extractExpiration(token).before(Date())
        logger.info("Проверка истечения срока действия токена: expired=$expired")
        return expired
    }

    private fun extractExpiration(token: String): Date {
        logger.info("Извлечение срока действия токена")
        return extractClaim(token, Claims::getExpiration)
    }

    private fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        logger.info("Извлечение claim из токена")
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    private fun extractAllClaims(token: String): Claims {
        logger.info("Извлечение всех claims из токена")
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .body
    }

    // generation util
    private fun generateToken(extraClaims: MutableMap<String, *>, userDetails: UserDetails): String {
        logger.info("Генерация JWT токена для username=${userDetails.username}")
        return Jwts.builder().setClaims(extraClaims).setSubject(userDetails.username)
            .setId(UUID.randomUUID().toString())
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(TOKEN_EXPIRATION_DAYS)))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256).compact()
    }

    private fun getSigningKey(): Key {
        logger.info("Получение ключа для подписи JWT токена")
        val keyBytes = Decoders.BASE64.decode(jwtSigningKey)
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
