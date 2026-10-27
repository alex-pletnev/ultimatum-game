package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.TestFixtures.offer
import edu.itmo.ultimatumgame.TestFixtures.round
import edu.itmo.ultimatumgame.TestFixtures.session
import edu.itmo.ultimatumgame.TestFixtures.user
import edu.itmo.ultimatumgame.dto.responses.AssignedOfferResponse
import edu.itmo.ultimatumgame.dto.responses.DecisionMadeResponse
import edu.itmo.ultimatumgame.dto.responses.OfferCreatedResponse
import edu.itmo.ultimatumgame.dto.responses.RoundResponse
import edu.itmo.ultimatumgame.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatumgame.model.Decision
import edu.itmo.ultimatumgame.util.DecisionMapper
import edu.itmo.ultimatumgame.util.OfferMapper
import edu.itmo.ultimatumgame.util.RoundMapper
import edu.itmo.ultimatumgame.util.SessionWithTeamsAndMembersMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID
import kotlin.test.Test

class EventPublisherServiceTest {

    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val offerMapper = mockk<OfferMapper>()
    private val decisionMapper = mockk<DecisionMapper>()
    private val roundMapper = mockk<RoundMapper>()
    private val sessionMapper = mockk<SessionWithTeamsAndMembersMapper>()
    private val service = EventPublisherService(
        messagingTemplate,
        offerMapper,
        decisionMapper,
        roundMapper,
        sessionMapper
    )

    @Test
    fun `publishOfferCreated шлёт DTO в topic session_offerCreated`() {
        val sessionId = UUID.randomUUID()
        val o = offer(proposer = user())
        val dto = mockk<OfferCreatedResponse>()
        every { offerMapper.toDto(o) } returns dto

        service.publishOfferCreated(sessionId, o)

        verify { messagingTemplate.convertAndSend("/topic/session/$sessionId/offerCreated", dto) }
    }

    @Test
    fun `publishOfferToPlayer шлёт AssignedOfferResponse в персональный топик player_userId_offer (T-058)`() {
        val sessionId = UUID.randomUUID()
        val responderId = UUID.randomUUID()
        val o = offer(proposer = user())
        val dto = mockk<AssignedOfferResponse>()
        every { offerMapper.toAssignedDto(o) } returns dto

        service.publishOfferToPlayer(sessionId, responderId, o)

        verify { messagingTemplate.convertAndSend("/topic/session/$sessionId/player/$responderId/offer", dto) }
    }

    @Test
    fun `publishDecisionMade шлёт DTO в topic session_decisionMade`() {
        val sessionId = UUID.randomUUID()
        val d = mockk<Decision> { every { session } returns null }
        val dto = mockk<DecisionMadeResponse>()
        every { decisionMapper.toDto(d) } returns dto

        service.publishDecisionMade(sessionId, d)

        verify { messagingTemplate.convertAndSend("/topic/session/$sessionId/decisionMade", dto) }
    }

    @Test
    fun `publishRoundStatus шлёт DTO в topic session_roundStatus`() {
        val sessionId = UUID.randomUUID()
        val r = round()
        val dto = mockk<RoundResponse>(relaxed = true)
        every { roundMapper.toDto(r) } returns dto

        service.publishRoundStatus(sessionId, r)

        verify { messagingTemplate.convertAndSend("/topic/session/$sessionId/roundStatus", dto) }
    }

    @Test
    fun `publishSessionStatus шлёт DTO в topic session_sessionStatus`() {
        val sessionId = UUID.randomUUID()
        val s = session()
        val dto = mockk<SessionWithTeamsAndMembersResponse>(relaxed = true)
        every { sessionMapper.toDto(s) } returns dto

        service.publishSessionStatus(sessionId, s)

        verify { messagingTemplate.convertAndSend("/topic/session/$sessionId/sessionStatus", dto) }
    }

    @Test
    fun `publishScoreUpdated шлёт SessionScoreDto в topic session_scoreUpdated`() {
        val sessionId = UUID.randomUUID()
        val score = edu.itmo.ultimatumgame.dto.responses.SessionScoreDto(
            roundSum = 100,
            roundsCompleted = 1,
            players = emptyList(),
            teams = emptyList()
        )

        service.publishScoreUpdated(sessionId, score)

        verify { messagingTemplate.convertAndSend("/topic/session/$sessionId/scoreUpdated", score) }
    }

    @Test
    fun `publishOffersShuffled шлёт OffersShuffledResponse в topic session_offersShuffled`() {
        val sessionId = UUID.randomUUID()
        val payload = edu.itmo.ultimatumgame.dto.responses.OffersShuffledResponse(
            roundNumber = 1,
            assignments = listOf(
                edu.itmo.ultimatumgame.dto.responses.OfferAssignment(
                    offerId = UUID.randomUUID(),
                    proposerId = UUID.randomUUID(),
                    responderId = UUID.randomUUID(),
                )
            )
        )

        service.publishOffersShuffled(sessionId, payload)

        verify { messagingTemplate.convertAndSend("/topic/session/$sessionId/offersShuffled", payload) }
    }

    @Test
    fun `session updates are delivered only after commit`() {
        val sessionId = UUID.randomUUID()
        val s = session()
        val dto = mockk<SessionWithTeamsAndMembersResponse>(relaxed = true)
        every { sessionMapper.toDto(s) } returns dto
        TransactionSynchronizationManager.initSynchronization()
        try {
            service.publishSessionStatus(sessionId, s)
            verify(exactly = 0) { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify(exactly = 1) {
                messagingTemplate.convertAndSend("/topic/session/$sessionId/sessionStatus", dto)
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `rolled back rounds do not notify players`() {
        val r = round()
        every { roundMapper.toDto(r) } returns mockk(relaxed = true)
        TransactionSynchronizationManager.initSynchronization()
        try {
            service.publishRoundStatus(UUID.randomUUID(), r)
            TransactionSynchronizationManager.getSynchronizations().forEach {
                it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            }
            verify(exactly = 0) { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }
}
