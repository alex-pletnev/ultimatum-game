package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.TestFixtures.offer
import edu.itmo.ultimatumgame.TestFixtures.round
import edu.itmo.ultimatumgame.TestFixtures.session
import edu.itmo.ultimatumgame.TestFixtures.user
import edu.itmo.ultimatumgame.model.Offer
import edu.itmo.ultimatumgame.model.RoundPhase
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreGameplayServiceTest {

    private val eventPublisher = mockk<EventPublisherService>(relaxUnitFun = true)
    private val service = CoreGameplayService(eventPublisher)

    @Test
    fun `initWaitDecisionsPhase — шафл через стратегию, публикация офферов респондентам, фаза OFFERS_SENT`() {
        val a = user(nickname = "A")
        val b = user(nickname = "B")
        val r = round()
        val oA = offer(proposer = a, round = r)
        val oB = offer(proposer = b, round = r)
        r.offers = mutableListOf(oA, oB)
        val s = session(members = mutableSetOf(a, b), currentRound = r)

        service.initWaitDecisionsPhase(s)

        assertEquals(RoundPhase.OFFERS_SENT, r.roundPhase)
        // после шафла у каждого оффера есть responder
        assertEquals(2, r.offers.mapNotNull { it.responder }.size)
        verify(exactly = 2) { eventPublisher.publishOfferToPlayer(s.id!!, any(), any<Offer>()) }
    }

    @Test
    fun `initWaitDecisionsPhase бросает если currentRound null`() {
        val a = user()
        val s = session(members = mutableSetOf(a), currentRound = null)
        assertThrows<IllegalStateException> { service.initWaitDecisionsPhase(s) }
    }
}
