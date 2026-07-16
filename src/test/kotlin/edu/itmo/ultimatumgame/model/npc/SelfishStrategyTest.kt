package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.NpcParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfishStrategyTest {

    @Test
    fun `offer = minOffer default is 0`() {
        val ctx = NpcTestFactories.offerCtx(roundSum = 100)
        assertEquals(0, SelfishStrategy(NpcParams.Selfish()).offer(ctx))
    }

    @Test
    fun `offer clamps to 0-roundSum`() {
        val ctx = NpcTestFactories.offerCtx(roundSum = 100)
        assertEquals(100, SelfishStrategy(NpcParams.Selfish(minOffer = 200)).offer(ctx))
        assertEquals(0, SelfishStrategy(NpcParams.Selfish(minOffer = -10)).offer(ctx))
    }

    @Test
    fun `decide accept any positive`() {
        val ctx = NpcTestFactories.decisionCtx(roundSum = 100, incomingAmount = 1)
        assertTrue(SelfishStrategy(NpcParams.Selfish()).decide(ctx))
    }

    @Test
    fun `decide reject zero`() {
        val ctx = NpcTestFactories.decisionCtx(roundSum = 100, incomingAmount = 0)
        assertFalse(SelfishStrategy(NpcParams.Selfish()).decide(ctx))
    }
}
