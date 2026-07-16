package edu.itmo.ultimatumgame.dto.responses

import edu.itmo.ultimatumgame.model.SessionType

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.SessionConfig}
 */
data class SessionConfigResponse(
    val sessionType: SessionType,
    val numRounds: Int,
    val numTeams: Int,
    val numPlayers: Int,
    val roundSum: Int,
    val timeoutMoveSec: Int,
    val autoAdvanceRounds: Boolean = false,
)
