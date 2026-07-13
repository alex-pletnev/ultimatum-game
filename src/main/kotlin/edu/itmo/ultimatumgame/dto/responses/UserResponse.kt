package edu.itmo.ultimatumgame.dto.responses

import edu.itmo.ultimatumgame.model.Role
import java.util.Date
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.User}
 */
data class UserResponse(
    val id: UUID,
    val nickname: String,
    val role: Role,
    val createdAt: Date,
)
