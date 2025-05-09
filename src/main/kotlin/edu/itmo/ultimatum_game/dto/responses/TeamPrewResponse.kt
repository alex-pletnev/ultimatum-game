package edu.itmo.ultimatum_game.dto.responses

import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.Team}
 */
data class TeamPrewResponse(
    val id: UUID,
    val name: String
) : Serializable