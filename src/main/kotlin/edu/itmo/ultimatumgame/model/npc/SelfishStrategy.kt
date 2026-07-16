package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.NpcParams

class SelfishStrategy(private val p: NpcParams.Selfish) : NpcStrategyPlayer {
    override fun offer(ctx: OfferCtx): Int = p.minOffer.coerceIn(0, ctx.roundSum())

    override fun decide(ctx: DecisionCtx): Boolean = ctx.incomingOffer.offerValue > 0
}
