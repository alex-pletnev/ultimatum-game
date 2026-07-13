package edu.itmo.ultimatumgame.dto.responses

import java.util.Date
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Decision}
 */
data class DecisionMadeResponse(
    val id: UUID,
    val round: RoundPrewResponse,
    val responder: UserResponse,
    val offer: OfferCreatedResponse,
    val decision: Boolean,
    val createdAt: Date,
)
