package edu.itmo.ultimatum_game.dto.requests

import edu.itmo.ultimatum_game.model.Role
import edu.itmo.ultimatum_game.util.toUuidOrThrow
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.*

data class CreateUserRequest(
    @field:NotBlank(message = "Имя обязательное поле")
    @field:Size(min = 3, max = 42, message = "Длина имени от 3 до 42 символов")
    val nickname: String?,
    val role: Role = Role.PLAYER
)

data class AuthenticateUserRequestDto(
    @field:NotBlank(message = "id обязателен и не может быть пустым")
    val id: String? = "",
)

fun AuthenticateUserRequestDto.toDomain(): AuthenticateUserRequest = AuthenticateUserRequest(id.toUuidOrThrow())

data class AuthenticateUserRequest(
    val id: UUID,
)

