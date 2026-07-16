package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.NpcParams

class RandomStrategy(private val p: NpcParams.Random) : NpcStrategyPlayer {
    override fun offer(ctx: OfferCtx): Int = ctx.random.nextInt(ctx.roundSum() + 1)

    override fun decide(ctx: DecisionCtx): Boolean = ctx.random.nextDouble() < p.acceptProbability
}
