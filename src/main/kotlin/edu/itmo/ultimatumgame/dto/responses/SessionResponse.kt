package edu.itmo.ultimatumgame.dto.responses

import edu.itmo.ultimatumgame.model.SessionState
import java.util.Date
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Session}
 */
data class SessionResponse(
    val id: UUID?,
    val displayName: String,
    val state: SessionState,
    val createdAt: Date,
    val admin: UserResponse,
    val openToConnect: Boolean,
    val rounds: MutableSet<RoundPrewResponse>,
    val config: SessionConfigResponse,
    val teams: MutableSet<TeamPrewResponse>,
    val currentRound: RoundPrewResponse? = null,
)
