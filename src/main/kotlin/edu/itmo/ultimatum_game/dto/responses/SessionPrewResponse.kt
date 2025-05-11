package edu.itmo.ultimatum_game.dto.responses

import edu.itmo.ultimatum_game.model.SessionState
import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.Session}
 */
data class SessionPrewResponse(
    val id: UUID,
    val displayName: String,
    val state: SessionState,
    val createdAt: Date,
    val admin: UserResponse,
    val openToConnect: Boolean,
    val config: SessionConfigResponse,
) : Serializable