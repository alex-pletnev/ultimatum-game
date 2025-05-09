package edu.itmo.ultimatum_game.dto.responses

import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.Decision}
 */
data class DecisionPrewResponse(
    val id: UUID? = null,
    val responder: UserResponse? = null,
    val offer: OfferPrewResponse? = null,
    val decision: Boolean? = null,
    val createdAt: Date = Date()
) : Serializable