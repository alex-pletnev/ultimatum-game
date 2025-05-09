package edu.itmo.ultimatum_game.controllers

import edu.itmo.ultimatum_game.dto.responses.CsrfTokenResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class CsrfController {

    @GetMapping("/csrf")
    fun csrf(csrfToken: CsrfToken): CsrfTokenResponse =
        CsrfTokenResponse(
            token = csrfToken.token,
            headerName = csrfToken.headerName,
            parameterName = csrfToken.parameterName
        )
}