package edu.itmo.ultimatumgame.dto.responses

import java.io.Serializable
import java.util.*

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
) : Serializable
