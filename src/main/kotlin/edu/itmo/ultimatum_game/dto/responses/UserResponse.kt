package edu.itmo.ultimatum_game.dto.responses

import edu.itmo.ultimatum_game.model.Role
import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.User}
 */
data class UserResponse(
    val id: UUID,
    val nickname: String,
    val role: Role,
    val createdAt: Date,
) : Serializable