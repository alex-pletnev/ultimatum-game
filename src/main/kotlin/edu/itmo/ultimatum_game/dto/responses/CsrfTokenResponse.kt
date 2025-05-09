package edu.itmo.ultimatum_game.dto.responses

import java.io.Serializable

data class CsrfTokenResponse(
    val token: String,
    val headerName: String,
    val parameterName: String
) : Serializable
