package edu.itmo.ultimatum_game.dto.responses

import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.Decision}
 */
data class DecisionMadeResponse(
    val id: UUID,
    val round: RoundPrewResponse,
    val responder: UserResponse,
    val offer: OfferCreatedResponse,
    val decision: Boolean,
    val createdAt: Date,
) : Serializable