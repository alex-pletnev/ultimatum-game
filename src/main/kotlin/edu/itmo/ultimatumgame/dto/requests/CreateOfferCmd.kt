package edu.itmo.ultimatumgame.dto.requests

import jakarta.validation.constraints.PositiveOrZero

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Offer}
 */
data class CreateOfferCmd(
    @field:PositiveOrZero(message = "Offer Должен содержать число больше либо равное 0")
    val amount: Int? = null
)
