package edu.itmo.ultimatumgame.controllers

import edu.itmo.ultimatumgame.dto.responses.UserIdResponse
import edu.itmo.ultimatumgame.dto.responses.UserResponse
import edu.itmo.ultimatumgame.exceptions.IdNotFoundException
import edu.itmo.ultimatumgame.services.UserService
import edu.itmo.ultimatumgame.util.UserMapper
import edu.itmo.ultimatumgame.util.logger
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user")
class UserController(
    private val userService: UserService,
    private val userMapper: UserMapper
) {

    private val logger = logger()

    @GetMapping("id")
    fun getCurrentUserId(): UserIdResponse {
        logger.info("Запрос на получение текущего userId")
        val user = userService.getCurrentUser()
        user.id?.let {
            logger.info("UserId успешно извлечён: $it")
            return UserIdResponse(it)
        }
        logger.error("Не удалось извлечь userId из контекста безопасности")
        throw IdNotFoundException("Не удалось извлечь id из контекста безопасности по jwt")
    }

    @GetMapping
    fun getCurrentUser(): UserResponse {
        logger.info("Запрос на получение текущего user")
        val user = userService.getCurrentUser()
        val dto = userMapper.toDto(user)
        return dto
    }
}
