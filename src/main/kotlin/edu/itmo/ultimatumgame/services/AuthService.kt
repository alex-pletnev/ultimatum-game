@file:Suppress("MaxLineLength", "MaximumLineLength", "UnsafeCallOnNullableType")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.requests.AuthenticateUserRequest
import edu.itmo.ultimatumgame.dto.requests.CreateUserRequest
import edu.itmo.ultimatumgame.dto.responses.JwtAuthenticationResponse
import edu.itmo.ultimatumgame.exceptions.UserRoleNotAllowedException
import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.model.User
import edu.itmo.ultimatumgame.util.AuthLogin
import edu.itmo.ultimatumgame.util.AuthRegister
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.logger
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val jwtService: JwtService,
    private val userService: UserService,
    private val domainEventLogger: DomainEventLogger,
) {

    private val logger = logger()

    fun quickLogin(authenticateUserRequest: AuthenticateUserRequest): JwtAuthenticationResponse {
        logger.info("Попытка быстрого входа для пользователя с id=${authenticateUserRequest.id}")
        val user = userService.getUserDetailService().invoke(authenticateUserRequest.id)
        val token = jwtService.generateToken(user)
        domainEventLogger.emit(AuthLogin(userId = user.id!!))
        return JwtAuthenticationResponse(token)
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
        user = userService.create(user)
        domainEventLogger.emit(AuthRegister(userId = user.id!!, nickname = user.nickname, role = user.role))
        val token = jwtService.generateToken(user)
        return JwtAuthenticationResponse(token)
    }
}
