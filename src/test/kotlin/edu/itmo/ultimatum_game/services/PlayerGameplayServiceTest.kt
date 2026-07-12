package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.TestFixtures.offer
import edu.itmo.ultimatum_game.TestFixtures.round
import edu.itmo.ultimatum_game.TestFixtures.session
import edu.itmo.ultimatum_game.TestFixtures.user
import edu.itmo.ultimatum_game.dto.requests.CreateOfferCmd
import edu.itmo.ultimatum_game.dto.requests.MakeDecisionCmd
import edu.itmo.ultimatum_game.exceptions.DuplicateIdException
import edu.itmo.ultimatum_game.exceptions.IdNotFoundException
import edu.itmo.ultimatum_game.model.Decision
import edu.itmo.ultimatum_game.model.Offer
import edu.itmo.ultimatum_game.model.Round
import edu.itmo.ultimatum_game.model.RoundPhase
import edu.itmo.ultimatum_game.model.Session
import edu.itmo.ultimatum_game.repositories.DecisionRepository
import edu.itmo.ultimatum_game.repositories.OfferRepository
import edu.itmo.ultimatum_game.repositories.RoundRepository
import edu.itmo.ultimatum_game.repositories.SessionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerGameplayServiceTest {

    private val eventPublisher = mockk<EventPublisherService>(relaxUnitFun = true)
    private val sessionRepo = mockk<SessionRepository>(relaxUnitFun = true)
    private val sessionService = mockk<SessionService>()
    private val roundRepo = mockk<RoundRepository>(relaxUnitFun = true)
    private val offerRepo = mockk<OfferRepository>()
    private val decisionRepo = mockk<DecisionRepository>()
    private val userService = mockk<UserService>()
    private val coreGameplay = mockk<CoreGameplayService>(relaxUnitFun = true)

    private val service = PlayerGameplayService(
        eventPublisher, sessionRepo, sessionService, roundRepo, offerRepo, decisionRepo, userService, coreGameplay
    )

    private fun stubOfferSaveIdentity() {
        every { offerRepo.save(any<Offer>()) } answers {
            val o = firstArg<Offer>()
            o.id = o.id ?: UUID.randomUUID()
            o
        }
    }

    private fun stubDecisionSaveIdentity() {
        every { decisionRepo.save(any<Decision>()) } answers {
            val d = firstArg<Decision>()
            d.id = d.id ?: UUID.randomUUID()
            d
        }
    }

    private fun stubRoundSave() {
        every { roundRepo.save(any<Round>()) } answers { firstArg() }
    }

    private fun stubSessionSave() {
        every { sessionRepo.save(any<Session>()) } answers { firstArg() }
    }

    // ----- sendOffer -----

    @Test
    fun `sendOffer — сохраняет offer, добавляет в round_offers, публикует OfferCreated`() {
        val a = user(); val b = user()
        val r = round(roundPhase = RoundPhase.WAIT_OFFERS)
        val s = session(members = mutableSetOf(a, b), currentRound = r)
        every { userService.getUserById(a.id!!) } returns a
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubOfferSaveIdentity()
        stubRoundSave()

        service.sendOffer(s.id!!, a.id!!, CreateOfferCmd(amount = 40))

        assertEquals(1, r.offers.size)
        assertEquals(a, r.offers[0].proposer)
        assertEquals(40, r.offers[0].offerValue)
        assertEquals(RoundPhase.WAIT_OFFERS, r.roundPhase, "фаза не меняется если ещё не все офферы")
        verify { eventPublisher.publishOfferCreated(s.id!!, any()) }
    }

    @Test
    fun `sendOffer — последний оффер переводит round в ALL_OFFERS_RECEIVED, вызывает CoreGameplay и публикует RoundStatus`() {
        val a = user(); val b = user()
        val r = round(roundPhase = RoundPhase.WAIT_OFFERS)
        val s = session(members = mutableSetOf(a, b), currentRound = r)
        // Уже один оффер от b
        r.offers += offer(proposer = b, round = r)
        every { userService.getUserById(a.id!!) } returns a
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubOfferSaveIdentity()
        stubRoundSave()
        stubSessionSave()

        service.sendOffer(s.id!!, a.id!!, CreateOfferCmd(amount = 55))

        assertEquals(RoundPhase.ALL_OFFERS_RECEIVED, r.roundPhase)
        verify { coreGameplay.initWaitDecisionsPhase(s) }
        verify { eventPublisher.publishRoundStatus(s.id!!, r) }
    }

    @Test
    fun `sendOffer — DuplicateIdException если игрок уже отправил offer в этом раунде`() {
        val a = user(); val b = user()
        val r = round()
        r.offers += offer(proposer = a, round = r)
        val s = session(members = mutableSetOf(a, b), currentRound = r)
        every { userService.getUserById(a.id!!) } returns a
        every { sessionService.getSessionEntity(s.id!!) } returns s

        assertThrows<DuplicateIdException> {
            service.sendOffer(s.id!!, a.id!!, CreateOfferCmd(amount = 10))
        }
    }

    @Test
    fun `sendOffer — бросает если currentRound null`() {
        val a = user()
        val s = session(members = mutableSetOf(a), currentRound = null)
        every { userService.getUserById(a.id!!) } returns a
        every { sessionService.getSessionEntity(s.id!!) } returns s

        assertThrows<IllegalStateException> {
            service.sendOffer(s.id!!, a.id!!, CreateOfferCmd(amount = 5))
        }
    }

    @Test
    fun `sendOffer — бросает если amount null`() {
        val a = user()
        val r = round()
        val s = session(members = mutableSetOf(a), currentRound = r)
        every { userService.getUserById(a.id!!) } returns a
        every { sessionService.getSessionEntity(s.id!!) } returns s

        assertThrows<IllegalStateException> {
            service.sendOffer(s.id!!, a.id!!, CreateOfferCmd(amount = null))
        }
    }

    // ----- makeDecision -----

    @Test
    fun `makeDecision — сохраняет decision, добавляет в round_decisions, публикует DecisionMade`() {
        val a = user(); val b = user()
        val r = round(roundPhase = RoundPhase.OFFERS_SENT)
        val offerFromB = offer(proposer = b, responder = a, round = r)
        r.offers += offerFromB
        val s = session(members = mutableSetOf(a, b), currentRound = r)
        every { userService.getUserById(a.id!!) } returns a
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubDecisionSaveIdentity()
        stubRoundSave()

        service.makeDecision(s.id!!, a.id!!, MakeDecisionCmd(offerId = offerFromB.id.toString(), decision = true))

        assertEquals(1, r.decisions.size)
        assertEquals(a, r.decisions[0].responder)
        assertEquals(true, r.decisions[0].decision)
        assertEquals(RoundPhase.OFFERS_SENT, r.roundPhase, "фаза не меняется если ещё не все решения")
        verify { eventPublisher.publishDecisionMade(s.id!!, any()) }
    }

    @Test
    fun `makeDecision — последнее решение переводит round в ALL_DECISIONS_RECEIVED и публикует RoundStatus`() {
        val a = user(); val b = user()
        val r = round(roundPhase = RoundPhase.OFFERS_SENT)
        val offA = offer(proposer = b, responder = a, round = r)
        val offB = offer(proposer = a, responder = b, round = r)
        r.offers.addAll(listOf(offA, offB))
        // Уже одно решение от b
        r.decisions += Decision(id = UUID.randomUUID(), round = r, responder = b, offer = offB, decision = true)
        val s = session(members = mutableSetOf(a, b), currentRound = r)
        every { userService.getUserById(a.id!!) } returns a
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubDecisionSaveIdentity()
        stubRoundSave()
        stubSessionSave()

        service.makeDecision(s.id!!, a.id!!, MakeDecisionCmd(offerId = offA.id.toString(), decision = false))

        assertEquals(RoundPhase.ALL_DECISIONS_RECEIVED, r.roundPhase)
        verify { eventPublisher.publishRoundStatus(s.id!!, r) }
    }

    @Test
    fun `makeDecision — DuplicateIdException если игрок уже принимал решение в этом раунде`() {
        val a = user(); val b = user()
        val r = round()
        val offB = offer(proposer = b, responder = a, round = r)
        r.offers += offB
        r.decisions += Decision(id = UUID.randomUUID(), round = r, responder = a, offer = offB, decision = true)
        val s = session(members = mutableSetOf(a, b), currentRound = r)
        every { userService.getUserById(a.id!!) } returns a
        every { sessionService.getSessionEntity(s.id!!) } returns s

        assertThrows<DuplicateIdException> {
            service.makeDecision(s.id!!, a.id!!, MakeDecisionCmd(offerId = offB.id.toString(), decision = false))
        }
    }

    @Test
    fun `makeDecision — IdNotFoundException если offerId не из этого раунда`() {
        val a = user(); val b = user()
        val r = round()
        r.offers += offer(proposer = b, responder = a, round = r)
        val s = session(members = mutableSetOf(a, b), currentRound = r)
        every { userService.getUserById(a.id!!) } returns a
        every { sessionService.getSessionEntity(s.id!!) } returns s

        val strangerId = UUID.randomUUID()
        assertThrows<IdNotFoundException> {
            service.makeDecision(s.id!!, a.id!!, MakeDecisionCmd(offerId = strangerId.toString(), decision = true))
        }
    }
}
