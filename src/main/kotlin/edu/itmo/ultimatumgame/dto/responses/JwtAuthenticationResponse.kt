package edu.itmo.ultimatumgame.dto.responses

/**
 * Ответ на auth-запросы (register/login/refresh).
 * - `accessToken` — короткоживущий (см. `JwtService.accessTokenTtlSeconds()`), несёт `type=ACCESS`.
 * - `refreshToken` — длинноживущий; заполнен при register/login, `null` при refresh (rotation отключён в MVP).
 * - `expiresIn` — TTL access-токена в секундах (совместимо с OAuth 2.0 Bearer).
 */
data class JwtAuthenticationResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
)
