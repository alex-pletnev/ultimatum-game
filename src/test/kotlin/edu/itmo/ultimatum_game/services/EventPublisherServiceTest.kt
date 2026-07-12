package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.TestFixtures.offer
import edu.itmo.ultimatum_game.TestFixtures.round
import edu.itmo.ultimatum_game.TestFixtures.session
import edu.itmo.ultimatum_game.TestFixtures.user
import edu.itmo.ultimatum_game.dto.responses.DecisionMadeResponse
import edu.itmo.ultimatum_game.dto.responses.OfferCreatedResponse
import edu.itmo.ultimatum_game.dto.responses.RoundResponse
import edu.itmo.ultimatum_game.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatum_game.model.Decision
import edu.itmo.ultimatum_game.util.DecisionMapper
import edu.itmo.ultimatum_game.util.OfferMapper
import edu.itmo.ultimatum_game.util.RoundMapper
import edu.itmo.ultimatum_game.util.SessionWithTeamsAndMembersMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.UUID
import kotlin.test.Test

class EventPublisherServiceTest {

    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val offerMapper = mockk<OfferMapper>()
    private val decisionMapper = mockk<DecisionMapper>()
    private val roundMapper = mockk<RoundMapper>()
    private val sessionMapper = mockk<SessionWithTeamsAndMembersMapper>()
    private val service = EventPublisherService(
        messagingTemplate, offerMapper, decisionMapper, roundMapper, sessionMapper
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
    fun `publishOfferToPlayer шлёт DTO в персональный топик player_userId_offer`() {
        val sessionId = UUID.randomUUID()
        val proposerId = UUID.randomUUID()
        val o = offer(proposer = user())
        val dto = mockk<OfferCreatedResponse>()
        every { offerMapper.toDto(o) } returns dto

        service.publishOfferToPlayer(sessionId, proposerId, o)

        verify { messagingTemplate.convertAndSend("/topic/session/$sessionId/player/$proposerId/offer", dto) }
    }

    @Test
    fun `publishDecisionMade шлёт DTO в topic session_decisionMade`() {
        val sessionId = UUID.randomUUID()
        val d = mockk<Decision>()
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
}
