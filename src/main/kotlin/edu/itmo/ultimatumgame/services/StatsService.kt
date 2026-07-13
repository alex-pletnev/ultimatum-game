@file:Suppress("UnsafeCallOnNullableType")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.responses.OfferStatsDto
import edu.itmo.ultimatumgame.dto.responses.SessionStatsDto
import edu.itmo.ultimatumgame.dto.responses.TeamInfo
import edu.itmo.ultimatumgame.dto.responses.UserInfo
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

        val offerDtos = offers.map { offer ->
            val dec = decisionByOffer[offer.id]
            OfferStatsDto(
                offerId = offer.id!!,
                amount = offer.offerValue,
                proposer = UserInfo(offer.proposer!!.id!!, offer.proposer!!.nickname),
                responder = offer.responder?.let { UserInfo(it.id!!, it.nickname) },
                proposerTeam = userIdToTeam[offer.proposer!!.id]?.toTeamInfo(),
                responderTeam = offer.responder?.let { userIdToTeam[it.id]?.toTeamInfo() },
                accepted = dec?.decision, // null, true или false
                roundNumber = offer.round!!.roundNumber,
                timestamp = offer.createdAt,
            )
        }.sortedBy { it.roundNumber }

        return SessionStatsDto(
            sessionId = session.id!!,
            displayName = session.displayName,
            state = session.state,
            createdAt = session.createdAt,
            totalRounds = session.rounds.size,
            decisionsCount = decisions.size,
            offers = offerDtos,
        )
    }

    private fun edu.itmo.ultimatumgame.model.Team.toTeamInfo() =
        TeamInfo(id = this.id!!, name = this.name)
}
