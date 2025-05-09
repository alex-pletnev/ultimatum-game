package edu.itmo.ultimatum_game.dto.requests

import jakarta.validation.constraints.PositiveOrZero
import java.io.Serializable

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.Offer}
 */
data class CreateOfferCmd(
    @field:PositiveOrZero(message = "Offer Должен содержать число больше либо равное 0")
    val amount: Int? = null
) : Serializable