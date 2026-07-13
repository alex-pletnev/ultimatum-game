package edu.itmo.ultimatumgame.dto.responses

import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Offer}
 */
data class OfferPrewResponse(
    val id: UUID? = null,
    val proposer: UserResponse? = null,
    val responder: UserResponse? = null,
    val offerValue: Int? = null,
    val createdAt: Date = Date()
) : Serializable
