package edu.itmo.ultimatumgame.dto.responses

import java.util.Date
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Decision}.
 *
 * T-073: defaults убраны из первичного конструктора (см. пояснение в OfferPrewResponse).
 */
data class DecisionPrewResponse(
    val id: UUID?,
    val responder: UserResponse?,
    val offer: OfferPrewResponse?,
    val decision: Boolean?,
    val createdAt: Date?,
)
