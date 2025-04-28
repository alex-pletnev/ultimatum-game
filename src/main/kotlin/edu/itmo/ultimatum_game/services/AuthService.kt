package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.dto.requests.AuthenticateUserRequest
import edu.itmo.ultimatum_game.dto.requests.CreateUserRequest
import edu.itmo.ultimatum_game.dto.responses.JwtAuthenticationResponse
import edu.itmo.ultimatum_game.model.User
import edu.itmo.ultimatum_game.util.logger
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val jwtService: JwtService,
    private val userService: UserService
) {

    private val logger = logger()

    fun quickLogin(authenticateUserRequest: AuthenticateUserRequest): JwtAuthenticationResponse {
        logger.info("Попытка быстрого входа для пользователя с id=${authenticateUserRequest.id}")
        val user = userService.getUserDetailService().invoke(authenticateUserRequest.id)
        val token = jwtService.generateToken(user)
        logger.info("Токен успешно сгенерирован для пользователя с id=${authenticateUserRequest.id}")
        return JwtAuthenticationResponse(token)
    }

    fun quickRegister(createUserRequest: CreateUserRequest): JwtAuthenticationResponse {
        logger.info("Попытка быстрой регистрации пользователя с ником='${createUserRequest.nickname}' и ролью=${createUserRequest.role}")
        var user = User(
            nickname = createUserRequest.nickname,
            role = createUserRequest.role,
        )
        user = userService.create(user)
        logger.info("Пользователь успешно создан с id=${user.id}")
        val token = jwtService.generateToken(user)
        logger.info("Токен успешно сгенерирован для нового пользователя с id=${user.id}")
        return JwtAuthenticationResponse(token)
    }
}
