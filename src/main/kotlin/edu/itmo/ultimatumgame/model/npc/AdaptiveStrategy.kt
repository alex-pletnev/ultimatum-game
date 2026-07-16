package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.NpcParams

class AdaptiveStrategy(private val p: NpcParams.Adaptive) : NpcStrategyPlayer {

    override fun offer(ctx: OfferCtx): Int {
        val roundSum = ctx.roundSum()
        val myOffers = ctx.myPastRounds.count { it.myOfferAmount != null }
        val fraction = if (myOffers == 0) {
            p.baselineFraction
        } else {
            val rejected = ctx.myPastRounds.count { it.myOfferAccepted == false }
            val rejectRate = rejected.toDouble() / myOffers
            (p.baselineFraction + p.slope * (rejectRate - p.targetRejectRate)).coerceIn(0.0, 1.0)
        }
        return (fraction * roundSum).toInt()
    }

    override fun decide(ctx: DecisionCtx): Boolean {
        // намеренно мягче Fair: принимаем всё выше половины baseline, иначе слишком агрессивно режектим
        val roundSum = ctx.roundSum()
        val threshold = (p.baselineFraction * roundSum).toInt() / 2
        return ctx.incomingOffer.offerValue >= threshold
    }
}
