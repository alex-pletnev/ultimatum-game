package edu.itmo.ultimatumgame.dto.responses

import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Team}
 */
data class TeamResponse(
    val id: UUID,
    val name: String,
    val members: MutableSet<UserResponse>,
) : Serializable
