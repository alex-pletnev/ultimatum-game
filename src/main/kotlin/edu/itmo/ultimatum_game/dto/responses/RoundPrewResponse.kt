package edu.itmo.ultimatum_game.dto.responses

import edu.itmo.ultimatum_game.model.RoundPhase
import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.Round}
 */
data class RoundPrewResponse(
    val id: UUID,
    val roundNumber: Int,
    val roundPhase: RoundPhase,
) : Serializable