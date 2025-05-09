package edu.itmo.ultimatum_game.dto.responses

import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.Offer}
 */
data class OfferPrewResponse(
    val id: UUID? = null,
    val proposer: UserResponse? = null,
    val responder: UserResponse? = null,
    val offerValue: Int? = null,
    val createdAt: Date = Date()
) : Serializable