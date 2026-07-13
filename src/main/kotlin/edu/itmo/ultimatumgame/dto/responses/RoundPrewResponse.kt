package edu.itmo.ultimatumgame.dto.responses

import edu.itmo.ultimatumgame.model.RoundPhase
import java.io.Serializable
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Round}
 */
data class RoundPrewResponse(
    val id: UUID,
    val roundNumber: Int,
    val roundPhase: RoundPhase,
) : Serializable
