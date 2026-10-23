package edu.itmo.ultimatumgame.dto.requests

import edu.itmo.ultimatumgame.model.NpcStrategy
import edu.itmo.ultimatumgame.model.SessionType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

private const val DEFAULT_SOLO_ROUNDS = 3
private const val MAX_SOLO_ROUNDS = 10L

data class SoloSessionRequest(
    val strategy: NpcStrategy = NpcStrategy.FAIR,
    val sessionType: SessionType = SessionType.FREE_FOR_ALL,
    @field:Min(1) @field:Max(MAX_SOLO_ROUNDS) val numRounds: Int = DEFAULT_SOLO_ROUNDS,
)
