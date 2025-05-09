package edu.itmo.ultimatum_game.dto.responses

import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.Decision}
 */
data class DecisionMadeResponse(
    val id: UUID? = null,
    val round: RoundPrewResponse? = null,
    val responder: UserResponse? = null,
    val offer: OfferCreatedResponse? = null,
    val decision: Boolean? = null,
    val createdAt: Date = Date()
) : Serializable