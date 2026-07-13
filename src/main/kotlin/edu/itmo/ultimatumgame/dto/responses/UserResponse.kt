package edu.itmo.ultimatumgame.dto.responses

import edu.itmo.ultimatumgame.model.Role
import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.User}
 */
data class UserResponse(
    val id: UUID,
    val nickname: String,
    val role: Role,
    val createdAt: Date,
) : Serializable
