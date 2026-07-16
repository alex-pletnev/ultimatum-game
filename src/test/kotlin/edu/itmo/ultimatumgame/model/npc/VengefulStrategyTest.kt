package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.NpcParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VengefulStrategyTest {

    @Test
    fun `baseline offer if no history`() {
        val ctx = NpcTestFactories.offerCtx(roundSum = 100, myPastRounds = emptyList())
        assertEquals(50, VengefulStrategy(NpcParams.Vengeful()).offer(ctx))
    }

    @Test
    fun `punish step after reject in prev round`() {
        val past = listOf(
            RoundOutcome(
                roundNumber = 1,
                myOfferAmount = 50,
                myOfferAccepted = false,
                incomingOfferAmount = null,
                incomingAccepted = null,
            )
        )
        val ctx = NpcTestFactories.offerCtx(roundSum = 100, myPastRounds = past)
        assertEquals(49, VengefulStrategy(NpcParams.Vengeful(punishStep = 1)).offer(ctx))
    }

    @Test
    fun `no punish if last offer was accepted`() {
        val past = listOf(
            RoundOutcome(1, myOfferAmount = 50, myOfferAccepted = true, null, null)
        )
        val ctx = NpcTestFactories.offerCtx(roundSum = 100, myPastRounds = past)
        assertEquals(50, VengefulStrategy(NpcParams.Vengeful()).offer(ctx))
    }

    @Test
    fun `decide raises threshold if last incoming was below baseline`() {
        val past = listOf(
            RoundOutcome(1, null, null, incomingOfferAmount = 20, incomingAccepted = false)
        )
        val strategy = VengefulStrategy(NpcParams.Vengeful(fairnessThreshold = 0.30))
        // effectiveThreshold = 0.30 + 0.05 = 0.35, roundSum = 100, threshold = 35
        val below = NpcTestFactories.decisionCtx(roundSum = 100, incomingAmount = 34, myPastRounds = past)
        assertFalse(strategy.decide(below))
        val above = NpcTestFactories.decisionCtx(roundSum = 100, incomingAmount = 35, myPastRounds = past)
        assertTrue(strategy.decide(above))
    }
}
