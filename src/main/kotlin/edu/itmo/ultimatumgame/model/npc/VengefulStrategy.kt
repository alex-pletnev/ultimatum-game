package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.NpcParams

class VengefulStrategy(private val p: NpcParams.Vengeful) : NpcStrategyPlayer {

    override fun offer(ctx: OfferCtx): Int {
        val roundSum = ctx.roundSum()
        val baseline = (p.baselineFraction * roundSum).toInt()
        val last = ctx.myPastRounds.lastOrNull() ?: return baseline
        return if (last.myOfferAccepted == false) {
            maxOf(0, (last.myOfferAmount ?: baseline) - p.punishStep)
        } else {
            baseline
        }
    }

    override fun decide(ctx: DecisionCtx): Boolean {
        val roundSum = ctx.roundSum()
        val lastIncoming = ctx.myPastRounds.lastOrNull()?.incomingOfferAmount
        val baselineIncoming = (p.baselineFraction * roundSum).toInt()
        val effectiveThreshold = if (lastIncoming != null && lastIncoming < baselineIncoming) {
            (p.fairnessThreshold + FAIRNESS_PUNISH_DELTA).coerceAtMost(FAIRNESS_MAX_THRESHOLD)
        } else {
            p.fairnessThreshold
        }
        return ctx.incomingOffer.offerValue >= (effectiveThreshold * roundSum).toInt()
    }

    companion object {
        private const val FAIRNESS_PUNISH_DELTA = 0.05
        private const val FAIRNESS_MAX_THRESHOLD = 0.5
    }
}
