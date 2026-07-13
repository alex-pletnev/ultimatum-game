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
    val score: SessionScoreDto,
)

data class OfferStatsDto(
    val offerId: UUID,
    val amount: Int,
    val proposer: UserInfo,
    val responder: UserInfo?,
    val proposerTeam: TeamInfo? = null,
    val responderTeam: TeamInfo? = null,
    val accepted: Boolean?, // null-если решения ещё нет
    val proposerScore: Int, // сколько получил proposer по итогам оффера
    val responderScore: Int, // сколько получил responder по итогам оффера
    val roundNumber: Int,
    val timestamp: Date,
)

data class SessionScoreDto(
    val roundSum: Int,
    val roundsCompleted: Int,
    val players: List<PlayerScoreDto>,
    val teams: List<TeamScoreDto>, // пустой для FREE_FOR_ALL
)

data class PlayerScoreDto(
    val userId: UUID,
    val nickname: String,
    val teamId: UUID?, // null для FREE_FOR_ALL
    val teamName: String?,
    val score: Int,
)

data class TeamScoreDto(
    val teamId: UUID,
    val name: String,
    val score: Int,
)

data class UserInfo(val id: UUID, val nickname: String)
data class TeamInfo(val id: UUID, val name: String)
