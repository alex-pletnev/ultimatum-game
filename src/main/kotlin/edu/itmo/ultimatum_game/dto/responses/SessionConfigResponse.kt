package edu.itmo.ultimatum_game.dto.responses

import edu.itmo.ultimatum_game.model.SessionType
import java.io.Serializable

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.SessionConfig}
 */
data class SessionConfigResponse(
    val sessionType: SessionType,
    val numRounds: Int,
    val numTeams: Int,
    val numPlayers: Int,
    val roundSum: Int,
    val timeoutMoveSec: Int,
) : Serializable