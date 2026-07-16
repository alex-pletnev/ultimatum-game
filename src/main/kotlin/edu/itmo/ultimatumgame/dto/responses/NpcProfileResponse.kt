package edu.itmo.ultimatumgame.dto.responses

import edu.itmo.ultimatumgame.model.NpcParams
import edu.itmo.ultimatumgame.model.NpcStrategy
import java.time.Instant
import java.util.UUID

data class NpcProfileResponse(
    val id: UUID,
    val userId: UUID,
    val nickname: String,
    val strategy: NpcStrategy,
    val params: NpcParams,
    val seed: Long?,
    val createdAt: Instant,
)

data class BulkNpcsResponse(
    val session: SessionWithTeamsAndMembersResponse,
    val npcs: List<NpcProfileResponse>,
)
