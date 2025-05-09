package edu.itmo.ultimatum_game.dto.responses

import java.io.Serializable
import java.util.*

data class ApiErrorResponse(
    val timestamp: Date,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
) : Serializable
