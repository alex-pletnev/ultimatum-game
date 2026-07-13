package edu.itmo.ultimatumgame.dto.responses

import java.io.Serializable
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Team}
 */
data class TeamPrewResponse(
    val id: UUID,
    val name: String
) : Serializable
