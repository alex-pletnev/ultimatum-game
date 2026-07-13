package edu.itmo.ultimatumgame.dto.responses

import java.io.Serializable
import java.util.Date
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Decision}
 */
data class DecisionPrewResponse(
    val id: UUID? = null,
    val responder: UserResponse? = null,
    val offer: OfferPrewResponse? = null,
    val decision: Boolean? = null,
    val createdAt: Date = Date()
) : Serializable
