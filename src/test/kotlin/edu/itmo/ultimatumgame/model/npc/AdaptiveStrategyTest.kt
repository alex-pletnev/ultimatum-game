package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.NpcParams
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveStrategyTest {

    @Test
    fun `no history — baseline offer`() {
        val ctx = NpcTestFactories.offerCtx(roundSum = 100, myPastRounds = emptyList())
        assertEquals(50, AdaptiveStrategy(NpcParams.Adaptive()).offer(ctx))
    }

    @Test
    fun `high rejectRate raises offerFraction`() {
        val past = (1..3).map {
            RoundOutcome(it, myOfferAmount = 20, myOfferAccepted = false, null, null)
        }
        val ctx = NpcTestFactories.offerCtx(roundSum = 100, myPastRounds = past)
        val offer = AdaptiveStrategy(
            NpcParams.Adaptive(baselineFraction = 0.5, targetRejectRate = 0.2, slope = 0.5)
        ).offer(ctx)
        // rejectRate=1.0, delta=0.8, offerFraction=0.5+0.5*0.8=0.9 → 90
        assertEquals(90, offer)
    }

    @Test
    fun `low rejectRate lowers offerFraction`() {
        val past = (1..3).map {
            RoundOutcome(it, myOfferAmount = 50, myOfferAccepted = true, null, null)
        }
        val ctx = NpcTestFactories.offerCtx(roundSum = 100, myPastRounds = past)
        val offer = AdaptiveStrategy(
            NpcParams.Adaptive(baselineFraction = 0.5, targetRejectRate = 0.2, slope = 0.5)
        ).offer(ctx)
        // rejectRate=0.0, delta=-0.2, offerFraction=0.5+0.5*(-0.2)=0.4 → 40
        assertEquals(40, offer)
    }

    @Test
    fun `fraction clamps to 0-1`() {
        val past = (1..3).map {
            RoundOutcome(it, myOfferAmount = 50, myOfferAccepted = true, null, null)
        }
        val ctx = NpcTestFactories.offerCtx(roundSum = 100, myPastRounds = past)
        // slope 100.0 сначала загонит fraction в отрицательное значение — должно clamp'нуться
        val offer = AdaptiveStrategy(
            NpcParams.Adaptive(baselineFraction = 0.1, targetRejectRate = 0.9, slope = 100.0)
        ).offer(ctx)
        assertEquals(0, offer)
    }

    @Test
    fun `decide accepts at baseline half`() {
        val ctx = NpcTestFactories.decisionCtx(roundSum = 100, incomingAmount = 25)
        assertEquals(
            true,
            AdaptiveStrategy(NpcParams.Adaptive(baselineFraction = 0.5)).decide(ctx),
        )
    }

    @Test
    fun `decide rejects below baseline half`() {
        val ctx = NpcTestFactories.decisionCtx(roundSum = 100, incomingAmount = 24)
        assertEquals(
            false,
            AdaptiveStrategy(NpcParams.Adaptive(baselineFraction = 0.5)).decide(ctx),
        )
    }
}
