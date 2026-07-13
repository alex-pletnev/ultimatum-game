package edu.itmo.ultimatumgame.dto.responses

import java.io.Serializable

data class JwtAuthenticationResponse(
    val token: String,
) : Serializable
