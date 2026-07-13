package edu.itmo.ultimatumgame.dto.responses

import java.util.Date
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Offer}
 */
data class OfferCreatedResponse(
    val id: UUID,
    val round: RoundPrewResponse,
    val proposer: UserResponse,
    val responder: UserResponse?,
    val offerValue: Int,
    val createdAt: Date,
)
