package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.NpcParams

class FairStrategy(private val p: NpcParams.Fair) : NpcStrategyPlayer {
    override fun offer(ctx: OfferCtx): Int = ctx.roundSum() / 2

    override fun decide(ctx: DecisionCtx): Boolean {
        val roundSum = ctx.roundSum()
        return ctx.incomingOffer.offerValue >= (p.fairnessThreshold * roundSum).toInt()
    }
}
