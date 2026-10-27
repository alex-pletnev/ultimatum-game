@file:Suppress("MaxLineLength", "MaximumLineLength", "UnsafeCallOnNullableType")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.requests.AuthenticateUserRequest
import edu.itmo.ultimatumgame.dto.requests.CreateUserRequest
import edu.itmo.ultimatumgame.dto.responses.JwtAuthenticationResponse
import edu.itmo.ultimatumgame.exceptions.InvalidJwtException
import edu.itmo.ultimatumgame.exceptions.UserRoleNotAllowedException
import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.model.User
import edu.itmo.ultimatumgame.util.AuthLogin
import edu.itmo.ultimatumgame.util.AuthRegister
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.UserLoggedOut
import edu.itmo.ultimatumgame.util.logger
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

private const val RECOVERY_CODE_BYTES = 32

@Service
class AuthService(
    private val jwtService: JwtService,
    private val userService: UserService,
    private val tokenRevocationService: TokenRevocationService,
    private val domainEventLogger: DomainEventLogger,
) {

    private val logger = logger()

    fun quickLogin(authenticateUserRequest: AuthenticateUserRequest): JwtAuthenticationResponse {
        val code = authenticateUserRequest.recoveryCode.trim()
        val user = userService.findByRecoveryCodeHash(hashCode(code))
            ?: throw InvalidJwtException("Неверный код восстановления")
        domainEventLogger.emit(AuthLogin(userId = user.id!!))
        return issueTokenPair(user).copy(recoveryCode = code)
    }

    fun quickRegister(createUserRequest: CreateUserRequest): JwtAuthenticationResponse {
        logger.info(
            "Попытка быстрой регистрации пользователя с ником='${createUserRequest.nickname}' и ролью=${createUserRequest.role}"
        )

        var user = User(
            nickname = createUserRequest.nickname!!,
            role = createUserRequest.role,
        )
        if (user.role == Role.NPC) throw UserRoleNotAllowedException("Роль 'NPC' недоступна к созданию таким образом")
        val code = newRecoveryCode()
        user.recoveryCodeHash = hashCode(code)
        user = userService.create(user)
        domainEventLogger.emit(AuthRegister(userId = user.id!!, nickname = user.nickname, role = user.role))
        return issueTokenPair(user).copy(recoveryCode = code)
    }

    @Transactional
    fun resetRecoveryCode(): Map<String, String> {
        val user = userService.getCurrentUser()
        val code = newRecoveryCode()
        user.recoveryCodeHash = hashCode(code)
        userService.save(user)
        return mapOf("recoveryCode" to code)
    }

    private fun newRecoveryCode(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(RECOVERY_CODE_BYTES).also { SecureRandom().nextBytes(it) })

    private fun hashCode(code: String): String = MessageDigest.getInstance("SHA-256")
        .digest(code.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun logout(bearerToken: String) {
        logger.info("Запрос logout — отзыв токена")
        val userId = UUID.fromString(jwtService.extractUsername(bearerToken))
        jwtService.extractJti(bearerToken)?.let(tokenRevocationService::revoke)
        domainEventLogger.emit(UserLoggedOut(userId = userId))
    }

    /**
     * Обменивает валидный refresh-токен на новый access-токен.
     * Rotation отключён в MVP — прежний refresh продолжает работать до `exp`.
     */
    fun refresh(refreshToken: String): JwtAuthenticationResponse {
        if (jwtService.extractType(refreshToken) != JwtService.TYPE_REFRESH) {
            throw InvalidJwtException("Ожидался refresh-токен")
        }
        val userId = UUID.fromString(jwtService.extractUsername(refreshToken))
        val user = userService.getUserDetailService().invoke(userId)
        if (!jwtService.isRefreshTokenValid(refreshToken, user)) {
            throw InvalidJwtException("Refresh-токен невалиден (истёк, отозван или подделан)")
        }
        return JwtAuthenticationResponse(
            accessToken = jwtService.generateAccessToken(user),
            refreshToken = null,
            expiresIn = jwtService.accessTokenTtlSeconds(),
        )
    }

    private fun issueTokenPair(user: User): JwtAuthenticationResponse =
        JwtAuthenticationResponse(
            accessToken = jwtService.generateAccessToken(user),
            refreshToken = jwtService.generateRefreshToken(user),
            expiresIn = jwtService.accessTokenTtlSeconds(),
        )
}
