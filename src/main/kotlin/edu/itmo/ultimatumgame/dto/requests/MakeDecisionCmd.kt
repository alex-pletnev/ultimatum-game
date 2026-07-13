package edu.itmo.ultimatumgame.dto.requests

import jakarta.validation.constraints.NotBlank

data class MakeDecisionCmd(
    @field:NotBlank
    val offerId: String = "",
    val decision: Boolean = true,
)
