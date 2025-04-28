package edu.itmo.ultimatum_game.controllers

import edu.itmo.ultimatum_game.dto.requests.AuthenticateUserRequest
import edu.itmo.ultimatum_game.dto.requests.CreateUserRequest
import edu.itmo.ultimatum_game.dto.responses.JwtAuthenticationResponse
import edu.itmo.ultimatum_game.services.AuthService
import edu.itmo.ultimatum_game.util.logger
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
@Validated
class AuthController(
    private val authService: AuthService,
) {

    private val logger = logger()


    @PostMapping("/quick-register")
    @PermitAll
    @ResponseStatus(HttpStatus.CREATED)
    fun quickRegister(@RequestBody @Valid createUserRequest: CreateUserRequest): JwtAuthenticationResponse {
        logger.info("Запрос быстрой регистрации: nickname='${createUserRequest.nickname}', role=${createUserRequest.role}")
        val jwtToken = authService.quickRegister(createUserRequest)
        logger.info("Токен выдан после регистрации для nickname='${createUserRequest.nickname}'")
        return jwtToken
    }

    @PostMapping("/quick-login")
    @PermitAll
    fun quickLogin(@RequestBody @Valid authenticateUserRequest: AuthenticateUserRequest): JwtAuthenticationResponse {
        logger.info("Запрос быстрого входа: id=${authenticateUserRequest.id}")
        val jwtToken = authService.quickLogin(authenticateUserRequest)
        logger.info("Токен выдан после входа для id=${authenticateUserRequest.id}")
        return jwtToken
    }




}