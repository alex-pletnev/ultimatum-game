package edu.itmo.ultimatumgame.controllers

import edu.itmo.ultimatumgame.configs.BEARER_PREFIX
import edu.itmo.ultimatumgame.configs.HEADER_AUTHORIZATION
import edu.itmo.ultimatumgame.dto.requests.AuthenticateUserRequestDto
import edu.itmo.ultimatumgame.dto.requests.CreateUserRequest
import edu.itmo.ultimatumgame.dto.requests.RefreshTokenRequest
import edu.itmo.ultimatumgame.dto.requests.toDomain
import edu.itmo.ultimatumgame.dto.responses.JwtAuthenticationResponse
import edu.itmo.ultimatumgame.services.AuthService
import edu.itmo.ultimatumgame.util.logger
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

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
        logger.info(
            "Запрос быстрой регистрации: nickname='${createUserRequest.nickname}', role=${createUserRequest.role}"
        )
        val jwtToken = authService.quickRegister(createUserRequest)
        logger.info("Токен выдан после регистрации для nickname='${createUserRequest.nickname}'")
        return jwtToken
    }

    @PostMapping("/quick-login")
    @PermitAll
    fun quickLogin(
        @RequestBody @Valid authenticateUserRequestDto: AuthenticateUserRequestDto
    ): JwtAuthenticationResponse {
        logger.info("Запрос быстрого входа: id=${authenticateUserRequestDto.id}")
        val authenticateUserRequest = authenticateUserRequestDto.toDomain()
        val jwtToken = authService.quickLogin(authenticateUserRequest)
        logger.info("Токен выдан после входа для id=${authenticateUserRequest.id}")
        return jwtToken
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@RequestHeader(HEADER_AUTHORIZATION) authorization: String) {
        val bearer = authorization.removePrefix(BEARER_PREFIX)
        authService.logout(bearer)
        logger.info("Токен отозван")
    }

    @PostMapping("/refresh")
    @PermitAll
    fun refresh(@RequestBody @Valid request: RefreshTokenRequest): JwtAuthenticationResponse {
        logger.info("Запрос refresh access-токена")
        return authService.refresh(request.refreshToken)
    }
}
