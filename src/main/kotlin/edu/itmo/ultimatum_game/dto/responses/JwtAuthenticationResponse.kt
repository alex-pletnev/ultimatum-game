package edu.itmo.ultimatum_game.dto.responses

import java.io.Serializable

data class JwtAuthenticationResponse(
    val token: String,
): Serializable
