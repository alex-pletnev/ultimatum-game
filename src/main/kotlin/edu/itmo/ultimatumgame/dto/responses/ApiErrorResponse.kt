package edu.itmo.ultimatumgame.dto.responses

import java.util.Date

data class ApiErrorResponse(
    val timestamp: Date,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
)
