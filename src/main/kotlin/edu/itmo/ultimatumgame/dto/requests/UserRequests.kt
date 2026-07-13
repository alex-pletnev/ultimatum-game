package edu.itmo.ultimatumgame.dto.requests

import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.util.toUuidOrThrow
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateUserRequest(
    @field:NotBlank(message = "Имя обязательное поле")
    @field:Size(min = 3, max = 42, message = "Длина имени от 3 до 42 символов")
    val nickname: String?,
    @field:Schema(
        allowableValues = ["ADMIN", "PLAYER", "OBSERVER"],
        description = "Роль пользователя. NPC на этом endpoint запрещена (403 UserRoleNotAllowedException).",
        defaultValue = "PLAYER"
    )
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
