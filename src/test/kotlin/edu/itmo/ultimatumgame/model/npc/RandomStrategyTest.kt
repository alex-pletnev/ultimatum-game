package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.NpcParams
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RandomStrategyTest {

    @Test
    fun `offer with fixed seed is deterministic`() {
        val ctx1 = NpcTestFactories.offerCtx(roundSum = 100, random = Random(42))
        val ctx2 = NpcTestFactories.offerCtx(roundSum = 100, random = Random(42))
        assertEquals(
            RandomStrategy(NpcParams.Random()).offer(ctx1),
            RandomStrategy(NpcParams.Random()).offer(ctx2),
        )
    }

    @Test
    fun `offer stays within 0-roundSum`() {
        val ctx = NpcTestFactories.offerCtx(roundSum = 100, random = Random(0))
        val amount = RandomStrategy(NpcParams.Random()).offer(ctx)
        assertTrue(amount in 0..100)
    }

    @Test
    fun `decide with acceptProbability 1_0 always accepts`() {
        val ctx = NpcTestFactories.decisionCtx(roundSum = 100, incomingAmount = 50, random = Random(0))
        assertTrue(RandomStrategy(NpcParams.Random(acceptProbability = 1.0)).decide(ctx))
    }

    @Test
    fun `decide with acceptProbability 0_0 always rejects`() {
        val ctx = NpcTestFactories.decisionCtx(roundSum = 100, incomingAmount = 50, random = Random(0))
        assertFalse(RandomStrategy(NpcParams.Random(acceptProbability = 0.0)).decide(ctx))
    }
}
