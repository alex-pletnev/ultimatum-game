package edu.itmo.ultimatum_game.dto.requests

import edu.itmo.ultimatum_game.model.SessionState
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.io.Serializable
import java.util.*

/**
 * DTO for {@link edu.itmo.ultimatum_game.model.Session}
 */
data class CreateSessionRequest(
    val id: UUID? = null,
    @field:Size(message = "Длина displayName должна быть от 3 и до 100 символов", min = 3, max = 100) @field:NotBlank(
        message = "displayName обязательное поле и не может быть пустым"
    ) val displayName: String? = null,
    val state: SessionState = SessionState.CREATED,
    @field:Valid @field:NotNull val config: SessionConfigDto? = null,
    val openToConnect: Boolean = true,
) : Serializable