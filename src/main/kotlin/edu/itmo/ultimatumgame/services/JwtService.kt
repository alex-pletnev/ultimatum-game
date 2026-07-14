// JWT lifecycle: генерация + валидация + claim extraction + revocation + type check —
// естественная когезия, дробление на несколько сервисов не оправдано.
@file:Suppress("TooManyFunctions")

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
        private const val ACCESS_TOKEN_TTL_MINUTES = 15L
        private const val REFRESH_TOKEN_TTL_DAYS = 14L
        private const val MILLIS_PER_SECOND = 1000L
        const val TYPE_ACCESS = "ACCESS"
        const val TYPE_REFRESH = "REFRESH"
        private const val CLAIM_TYPE = "type"
    }

    fun accessTokenTtlSeconds(): Long = TimeUnit.MINUTES.toSeconds(ACCESS_TOKEN_TTL_MINUTES)

    fun generateAccessToken(userDetails: UserDetails): String {
        val claims = HashMap<String, Any>()
        if (userDetails is User) {
            claims["nickname"] = userDetails.nickname
            claims["role"] = userDetails.role
            claims["createdAt"] = userDetails.createdAt
        }
        claims[CLAIM_TYPE] = TYPE_ACCESS
        return buildToken(claims, userDetails, TimeUnit.MINUTES.toMillis(ACCESS_TOKEN_TTL_MINUTES))
    }

    fun generateRefreshToken(userDetails: UserDetails): String {
        val claims = HashMap<String, Any>()
        claims[CLAIM_TYPE] = TYPE_REFRESH
        return buildToken(claims, userDetails, TimeUnit.DAYS.toMillis(REFRESH_TOKEN_TTL_DAYS))
    }

    fun extractUsername(token: String): String {
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

    /** Возвращает `type` claim или `null`, если отсутствует. */
    fun extractType(token: String): String? =
        runCatching { extractClaim(token) { it[CLAIM_TYPE] as? String } }.getOrNull()

    /** Секунды до `exp`. Отрицательное значение = уже истёк. */
    fun extractTtlSeconds(token: String): Long {
        val expMillis = extractClaim(token, Claims::getExpiration).time
        return (expMillis - System.currentTimeMillis()) / MILLIS_PER_SECOND
    }

    /**
     * Валидность **access**-токена: не истёк, subject совпадает, не отозван,
     * type == ACCESS. Refresh-токены отклоняются — их проверяем отдельно
     * через [isRefreshTokenValid].
     */
    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val isExpired = isTokenExpired(token)
        val usernameMatches = extractUsername(token) == userDetails.username
        val isRevoked = extractJti(token)?.let(tokenRevocationService::isRevoked) ?: false
        val isAccessType = extractType(token) == TYPE_ACCESS
        logger.debug(
            "Access-token check: expired=$isExpired, usernameOk=$usernameMatches, " +
                "revoked=$isRevoked, accessType=$isAccessType"
        )
        return !isExpired && usernameMatches && !isRevoked && isAccessType
    }

    /** Валидность **refresh**-токена: не истёк, subject совпадает, не отозван, type == REFRESH. */
    fun isRefreshTokenValid(token: String, userDetails: UserDetails): Boolean {
        val isExpired = isTokenExpired(token)
        val usernameMatches = extractUsername(token) == userDetails.username
        val isRevoked = extractJti(token)?.let(tokenRevocationService::isRevoked) ?: false
        val isRefreshType = extractType(token) == TYPE_REFRESH
        logger.debug(
            "Refresh-token check: expired=$isExpired, usernameOk=$usernameMatches, " +
                "revoked=$isRevoked, refreshType=$isRefreshType"
        )
        return !isExpired && usernameMatches && !isRevoked && isRefreshType
    }

    // extraction util
    private fun isTokenExpired(token: String): Boolean =
        extractClaim(token, Claims::getExpiration).before(Date())

    private fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    private fun extractAllClaims(token: String): Claims =
        Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .body

    // generation util
    private fun buildToken(
        extraClaims: MutableMap<String, *>,
        userDetails: UserDetails,
        ttlMillis: Long,
    ): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .setClaims(extraClaims)
            .setSubject(userDetails.username)
            .setId(UUID.randomUUID().toString())
            .setIssuedAt(Date(now))
            .setExpiration(Date(now + ttlMillis))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact()
    }

    private fun getSigningKey(): Key {
        val keyBytes = Decoders.BASE64.decode(jwtSigningKey)
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
