package edu.itmo.ultimatumgame.dto.responses

import edu.itmo.ultimatumgame.model.RoundPhase
import java.io.Serializable
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Round}
 */
data class RoundResponse(
    val id: UUID,
    val roundNumber: Int,
    val roundPhase: RoundPhase,
    val offers: MutableList<OfferPrewResponse>,
    val decisions: MutableList<DecisionPrewResponse>,
    val session: SessionPrewResponse,
) : Serializable
