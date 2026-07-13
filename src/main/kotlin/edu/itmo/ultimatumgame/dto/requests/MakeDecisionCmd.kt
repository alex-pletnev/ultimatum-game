package edu.itmo.ultimatumgame.dto.requests

import jakarta.validation.constraints.NotBlank
import java.io.Serializable

data class MakeDecisionCmd(
    @field:NotBlank
    val offerId: String = "",
    val decision: Boolean = true,
) : Serializable
