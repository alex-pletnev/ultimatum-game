@file:Suppress("UnsafeCallOnNullableType")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.responses.OfferStatsDto
import edu.itmo.ultimatumgame.dto.responses.PlayerScoreDto
import edu.itmo.ultimatumgame.dto.responses.SessionScoreDto
import edu.itmo.ultimatumgame.dto.responses.SessionStatsDto
import edu.itmo.ultimatumgame.dto.responses.TeamInfo
import edu.itmo.ultimatumgame.dto.responses.TeamScoreDto
import edu.itmo.ultimatumgame.dto.responses.UserInfo
import edu.itmo.ultimatumgame.model.Decision
import edu.itmo.ultimatumgame.model.Offer
import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.model.SessionType
import edu.itmo.ultimatumgame.repositories.DecisionRepository
import edu.itmo.ultimatumgame.repositories.OfferRepository
import edu.itmo.ultimatumgame.repositories.SessionRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StatsService(
    private val sessionRepository: SessionRepository,
    private val offerRepository: OfferRepository,
    private val decisionRepository: DecisionRepository,
) {

    /**
     * @throws EntityNotFoundException если сессия не найдена
     */
    @Transactional(readOnly = true)
    fun getSessionStats(sessionId: UUID): SessionStatsDto {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { EntityNotFoundException("Session $sessionId not found") }

        val offers = offerRepository.findAllBySessionIdWithRelations(sessionId)
        val decisions = decisionRepository.findAllBySessionIdWithRelations(sessionId)
        val decisionByOffer = decisions.associateBy { it.offer!!.id }

        val teamBattle = session.config?.sessionType == SessionType.TEAM_BATTLE
        val userIdToTeam = if (teamBattle) {
            session.teams.flatMap { team -> team.members.map { it.id to team } }.toMap()
        } else {
            emptyMap()
        }

        val roundSum = session.config?.roundSum ?: 0

        val offerDtos = offers.map { offer ->
            val dec = decisionByOffer[offer.id]
            val (pScore, rScore) = computeScores(offer, dec, roundSum)
            OfferStatsDto(
                offerId = offer.id!!,
                amount = offer.offerValue,
                proposer = UserInfo(offer.proposer!!.id!!, offer.proposer!!.nickname),
                responder = offer.responder?.let { UserInfo(it.id!!, it.nickname) },
                proposerTeam = userIdToTeam[offer.proposer!!.id]?.toTeamInfo(),
                responderTeam = offer.responder?.let { userIdToTeam[it.id]?.toTeamInfo() },
                accepted = dec?.decision, // null, true или false
                proposerScore = pScore,
                responderScore = rScore,
                roundNumber = offer.round!!.roundNumber,
                timestamp = offer.createdAt,
            )
        }.sortedBy { it.roundNumber }

        val score = buildSessionScore(session, offerDtos, roundSum, teamBattle, userIdToTeam)

        return SessionStatsDto(
            sessionId = session.id!!,
            displayName = session.displayName,
            state = session.state,
            createdAt = session.createdAt,
            totalRounds = session.rounds.size,
            decisionsCount = decisions.size,
            offers = offerDtos,
            score = score,
        )
    }

    private fun computeScores(offer: Offer, decision: Decision?, roundSum: Int): Pair<Int, Int> {
        return when (decision?.decision) {
            true -> (roundSum - offer.offerValue) to offer.offerValue
            false -> 0 to 0
            null -> 0 to 0
        }
    }

    private fun buildSessionScore(
        session: Session,
        offerDtos: List<OfferStatsDto>,
        roundSum: Int,
        teamBattle: Boolean,
        userIdToTeam: Map<UUID?, edu.itmo.ultimatumgame.model.Team>,
    ): SessionScoreDto {
        val playerScores = mutableMapOf<UUID, Int>()
        for (row in offerDtos) {
            playerScores.merge(row.proposer.id, row.proposerScore) { a, b -> a + b }
            row.responder?.let { playerScores.merge(it.id, row.responderScore) { a, b -> a + b } }
        }

        val players = session.members.map { u ->
            val userId = u.id!!
            val team = if (teamBattle) userIdToTeam[userId] else null
            PlayerScoreDto(
                userId = userId,
                nickname = u.nickname,
                teamId = team?.id,
                teamName = team?.name,
                score = playerScores.getOrDefault(userId, 0),
            )
        }.sortedByDescending { it.score }

        val teams = if (teamBattle) {
            session.teams.map { t ->
                TeamScoreDto(
                    teamId = t.id!!,
                    name = t.name,
                    score = t.members.sumOf { playerScores.getOrDefault(it.id!!, 0) },
                )
            }.sortedByDescending { it.score }
        } else {
            emptyList()
        }

        val roundsCompleted = session.rounds.count { it.decisions.size == session.members.size }

        return SessionScoreDto(
            roundSum = roundSum,
            roundsCompleted = roundsCompleted,
            players = players,
            teams = teams,
        )
    }

    private fun edu.itmo.ultimatumgame.model.Team.toTeamInfo() =
        TeamInfo(id = this.id!!, name = this.name)
}
