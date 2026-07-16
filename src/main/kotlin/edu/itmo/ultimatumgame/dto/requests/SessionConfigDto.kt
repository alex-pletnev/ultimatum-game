@file:Suppress("MaxLineLength", "MaximumLineLength")

package edu.itmo.ultimatumgame.dto.requests

import edu.itmo.ultimatumgame.model.SessionType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.hibernate.validator.constraints.Range

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.SessionConfig}
 */
@Schema(
    description = "Конфигурация сессии. Кросс-инвариант: для FREE_FOR_ALL numTeams=0; для TEAM_BATTLE numTeams>=2. Числовые поля не должны быть null (валидируется на уровне сервиса)."
)
data class SessionConfigDto(
    val sessionType: SessionType = SessionType.FREE_FOR_ALL,
    @field:Min(message = "Минимальное numRounds это 1", value = 1)
    @field:Max(message = "Максимальное numRounds это 10", value = 10)
    @field:Positive(message = "numRounds должно быть положительным")
    val numRounds: Int? = null,
    @field:Max(message = "Максимальное numTeams - 5", value = 5)
    @field:PositiveOrZero(message = "numTeams не может быть отрицательным. numTeams - 0 если игра без команд")
    val numTeams: Int = 0,
    @field:Range(message = "numPlayers это число от 2 и до 120", min = 2, max = 120)
    val numPlayers: Int? = null,
    @field:Range(message = "roundSum это число от 10 и до 100000", min = 10, max = 100000)
    val roundSum: Int? = null,
    @field:Range(message = "timeoutMoveSec это число от 10 и до 300", min = 10, max = 300)
    val timeoutMoveSec: Int? = null,
    @field:Schema(
        description = "Автоматически прогонять следующий раунд после закрытия текущего (для all-NPC симуляции).",
    )
    val autoAdvanceRounds: Boolean = false,
)
