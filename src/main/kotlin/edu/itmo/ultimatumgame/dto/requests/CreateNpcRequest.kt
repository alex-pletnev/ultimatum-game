package edu.itmo.ultimatumgame.dto.requests

import edu.itmo.ultimatumgame.model.NpcParams
import edu.itmo.ultimatumgame.model.NpcStrategy
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Создание NPC-игрока с выбранной стратегией и её параметрами.")
data class CreateNpcRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 100)
    val nickname: String,
    val strategy: NpcStrategy,
    val params: NpcParams,
    val seed: Long? = null,
)

@Schema(description = "Bulk-создание NPC для сессии.")
data class BulkNpcsRequest(
    val count: Int,
    val strategy: NpcStrategy,
    val params: NpcParams,
    val seedBase: Long? = null,
)

@Schema(description = "Подключение существующего NPC к сессии.")
data class JoinNpcRequest(
    val npcId: java.util.UUID,
    val teamId: java.util.UUID? = null,
)
