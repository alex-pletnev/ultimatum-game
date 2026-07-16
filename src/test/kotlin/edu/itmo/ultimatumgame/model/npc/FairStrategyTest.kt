package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.NpcParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FairStrategyTest {

    @Test
    fun `offer = roundSum div 2`() {
        val ctx = NpcTestFactories.offerCtx(roundSum = 100)
        assertEquals(50, FairStrategy(NpcParams.Fair()).offer(ctx))
    }

    @Test
    fun `decide accept at threshold`() {
        val ctx = NpcTestFactories.decisionCtx(roundSum = 100, incomingAmount = 30)
        assertTrue(FairStrategy(NpcParams.Fair(fairnessThreshold = 0.30)).decide(ctx))
    }

    @Test
    fun `decide reject below threshold`() {
        val ctx = NpcTestFactories.decisionCtx(roundSum = 100, incomingAmount = 29)
        assertFalse(FairStrategy(NpcParams.Fair(fairnessThreshold = 0.30)).decide(ctx))
    }
}
