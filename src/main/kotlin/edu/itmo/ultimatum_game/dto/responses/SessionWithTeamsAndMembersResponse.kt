package edu.itmo.ultimatum_game.dto.responses

import edu.itmo.ultimatum_game.model.SessionState
import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.Session}
 */
data class SessionWithTeamsAndMembersResponse(
    val id: UUID,
    val displayName: String,
    val state: SessionState,
    val createdAt: Date,
    val admin: UserResponse,
    val openToConnect: Boolean,
    val currentRound: RoundPrewResponse? = null,
    val config: SessionConfigResponse,
    val teams: MutableSet<TeamResponse>,
    val members: MutableSet<UserResponse>,
    val observers: MutableSet<UserResponse>,
) : Serializable