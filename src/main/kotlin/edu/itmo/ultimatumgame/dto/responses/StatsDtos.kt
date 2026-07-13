package edu.itmo.ultimatumgame.dto.responses

import edu.itmo.ultimatumgame.model.SessionState
import java.util.Date
import java.util.UUID

data class SessionStatsDto(
    val sessionId: UUID,
    val displayName: String,
    val state: SessionState,
    val createdAt: Date,
    val totalRounds: Int,
    val decisionsCount: Int,
    val offers: List<OfferStatsDto>,
)

data class OfferStatsDto(
    val offerId: UUID,
    val amount: Int,
    val proposer: UserInfo,
    val responder: UserInfo?,
    val proposerTeam: TeamInfo? = null,
    val responderTeam: TeamInfo? = null,
    val accepted: Boolean?, // null-если решения ещё нет
    val roundNumber: Int,
    val timestamp: Date,
)

data class UserInfo(val id: UUID, val nickname: String)
data class TeamInfo(val id: UUID, val name: String)
