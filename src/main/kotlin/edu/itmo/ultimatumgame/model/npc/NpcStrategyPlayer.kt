package edu.itmo.ultimatumgame.model.npc

import edu.itmo.ultimatumgame.model.Offer
import edu.itmo.ultimatumgame.model.Round
import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.model.User
import java.util.Random

/**
 * Итог одного прошлого раунда с точки зрения конкретного NPC. Используется memory-стратегиями.
 * Все поля nullable: NPC мог не выступать proposer'ом или respondent'ом в конкретном раунде.
 */
data class RoundOutcome(
    val roundNumber: Int,
    val myOfferAmount: Int?,
    val myOfferAccepted: Boolean?,
    val incomingOfferAmount: Int?,
    val incomingAccepted: Boolean?,
)

data class OfferCtx(
    val session: Session,
    val round: Round,
    val me: User,
    val myPastRounds: List<RoundOutcome>,
    val random: Random,
)

data class DecisionCtx(
    val session: Session,
    val round: Round,
    val me: User,
    val incomingOffer: Offer,
    val myPastRounds: List<RoundOutcome>,
    val random: Random,
)

interface NpcStrategyPlayer {
    fun offer(ctx: OfferCtx): Int
    fun decide(ctx: DecisionCtx): Boolean
}

internal fun OfferCtx.roundSum(): Int =
    checkNotNull(session.config) { "SessionConfig обязателен для NPC-стратегии" }.roundSum

internal fun DecisionCtx.roundSum(): Int =
    checkNotNull(session.config) { "SessionConfig обязателен для NPC-стратегии" }.roundSum
