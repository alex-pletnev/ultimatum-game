@file:Suppress(
    "MaxLineLength",
    "MaximumLineLength",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
    "UnsafeCallOnNullableType",
    "ReturnCount",
)

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.model.Decision
import edu.itmo.ultimatumgame.model.NpcParams
import edu.itmo.ultimatumgame.model.NpcProfile
import edu.itmo.ultimatumgame.model.NpcStrategy
import edu.itmo.ultimatumgame.model.Offer
import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.model.Round
import edu.itmo.ultimatumgame.model.RoundPhase
import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.model.User
import edu.itmo.ultimatumgame.model.npc.AdaptiveStrategy
import edu.itmo.ultimatumgame.model.npc.DecisionCtx
import edu.itmo.ultimatumgame.model.npc.FairStrategy
import edu.itmo.ultimatumgame.model.npc.NpcStrategyPlayer
import edu.itmo.ultimatumgame.model.npc.OfferCtx
import edu.itmo.ultimatumgame.model.npc.RandomStrategy
import edu.itmo.ultimatumgame.model.npc.RoundOutcome
import edu.itmo.ultimatumgame.model.npc.SelfishStrategy
import edu.itmo.ultimatumgame.model.npc.VengefulStrategy
import edu.itmo.ultimatumgame.repositories.DecisionRepository
import edu.itmo.ultimatumgame.repositories.NpcProfileRepository
import edu.itmo.ultimatumgame.repositories.OfferRepository
import edu.itmo.ultimatumgame.repositories.RoundRepository
import edu.itmo.ultimatumgame.repositories.SessionRepository
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.NpcStrategyFailed
import edu.itmo.ultimatumgame.util.RoundClosed
import edu.itmo.ultimatumgame.util.logger
import jakarta.transaction.Transactional
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.util.Random

/**
 * Оркестрирует ходы NPC. Синхронно триггерится из двух точек существующего gameplay'а:
 *  - `playOffers(round)` — после установки `WAIT_OFFERS` (start/nextRound).
 *  - `playDecisions(round)` — после `initWaitDecisionsPhase` (OFFERS_SENT).
 *
 * При крахе стратегии — fallback на `FairStrategy`, событие `npc.strategy.failed`.
 */
@Service
class NpcService(
    private val npcProfileRepo: NpcProfileRepository,
    private val offerRepo: OfferRepository,
    private val decisionRepo: DecisionRepository,
    private val roundRepo: RoundRepository,
    private val sessionRepository: SessionRepository,
    private val eventPublisher: EventPublisherService,
    private val domainEventLogger: DomainEventLogger,
    @Lazy private val coreGameplayService: CoreGameplayService,
    private val statsService: StatsService,
    @Lazy private val adminGameplayService: AdminGameplayService,
) {

    private val logger = logger()

    @Transactional
    fun playOffers(round: Round) {
        val session = round.session ?: error("round.session не должен быть null для playOffers")
        if (round.roundPhase != RoundPhase.WAIT_OFFERS) {
            logger.debug("playOffers: пропущено, phase={} (ожидается WAIT_OFFERS)", round.roundPhase)
            return
        }
        val pendingNpcs = session.members
            .filter { it.role == Role.NPC && round.offers.none { off -> off.proposer?.id == it.id } }
        if (pendingNpcs.isEmpty()) return
        logger.info("playOffers: {} NPC-игроков ожидают offer в сессии {}", pendingNpcs.size, session.id)

        for (npc in pendingNpcs) {
            val profile = npcProfileRepo.findByUserId(npc.id!!)
            if (profile == null) {
                logger.warn("playOffers: NPC {} без профиля — пропущен", npc.id)
                continue
            }
            val ctx = buildOfferCtx(session, round, npc, profile)
            val amount = runStrategyOffer(profile, session, round, npc, ctx)
            val clamped = amount.coerceIn(0, session.config!!.roundSum)
            val offer = offerRepo.save(
                Offer(session = session, round = round, proposer = npc, offerValue = clamped)
            )
            round.offers += offer
            roundRepo.save(round)
            eventPublisher.publishOfferCreated(session.id!!, offer)
        }

        // Если все офферы собраны — переводим фазу и запускаем shuffle + playDecisions.
        if (round.offers.size == session.members.size) {
            round.roundPhase = RoundPhase.ALL_OFFERS_RECEIVED
            coreGameplayService.initWaitDecisionsPhase(session)
            sessionRepository.save(session)
            eventPublisher.publishRoundStatus(session.id!!, round)
            playDecisions(round)
        }
    }

    @Transactional
    fun playDecisions(round: Round) {
        val session = round.session ?: error("round.session не должен быть null для playDecisions")
        if (round.roundPhase != RoundPhase.OFFERS_SENT) {
            logger.debug("playDecisions: пропущено, phase={} (ожидается OFFERS_SENT)", round.roundPhase)
            return
        }
        data class NpcAssignment(val npc: User, val incoming: Offer)

        val pendingAssignments: List<NpcAssignment> = session.members
            .filter { it.role == Role.NPC }
            .filter { npc -> round.decisions.none { it.responder?.id == npc.id } }
            .mapNotNull { npc ->
                val incoming = round.offers.find { it.responder?.id == npc.id } ?: return@mapNotNull null
                NpcAssignment(npc, incoming)
            }
        if (pendingAssignments.isEmpty()) return
        logger.info("playDecisions: {} NPC-игроков ожидают decision в сессии {}", pendingAssignments.size, session.id)

        for ((npc, incoming) in pendingAssignments) {
            val profile = npcProfileRepo.findByUserId(npc.id!!)
            if (profile == null) {
                logger.warn("playDecisions: NPC {} без профиля — пропущен", npc.id)
                continue
            }
            val ctx = buildDecisionCtx(session, round, npc, incoming, profile)
            val accepted = runStrategyDecide(profile, session, round, npc, ctx)
            val decision = decisionRepo.save(
                Decision(
                    session = session,
                    round = round,
                    responder = npc,
                    offer = incoming,
                    decision = accepted,
                )
            )
            round.decisions += decision
            roundRepo.save(round)
            eventPublisher.publishDecisionMade(session.id!!, decision)
        }

        if (round.decisions.size == session.members.size) {
            round.roundPhase = RoundPhase.ALL_DECISIONS_RECEIVED
            sessionRepository.save(session)
            eventPublisher.publishRoundStatus(session.id!!, round)
            val score = statsService.getSessionStats(session.id!!).score
            eventPublisher.publishScoreUpdated(session.id!!, score)
            domainEventLogger.emit(
                RoundClosed(sessionId = session.id!!, roundId = round.id!!, roundNumber = round.roundNumber)
            )
            triggerAutoAdvanceIfEnabled(session, round)
        }
    }

    private fun triggerAutoAdvanceIfEnabled(session: Session, round: Round) {
        val cfg = session.config ?: return
        if (!cfg.autoAdvanceRounds) return
        if (session.state != edu.itmo.ultimatumgame.model.SessionState.RUNNING) return
        if (round.roundNumber >= cfg.numRounds) return
        logger.info("autoAdvanceRounds включён — старт следующего раунда сессии {} из NpcService", session.id)
        adminGameplayService.startNextRound(session.id!!)
    }

    private fun runStrategyOffer(
        profile: NpcProfile,
        session: Session,
        round: Round,
        npc: User,
        ctx: OfferCtx,
    ): Int = try {
        strategyOf(profile).offer(ctx)
    } catch (e: Exception) {
        logger.error("NPC strategy offer failed for user={}, fallback FAIR", npc.id, e)
        domainEventLogger.emit(
            NpcStrategyFailed(
                sessionId = session.id!!,
                roundId = round.id!!,
                userId = npc.id!!,
                strategy = profile.strategy.name,
                phase = "offer",
            )
        )
        FairStrategy(NpcParams.Fair()).offer(ctx)
    }

    private fun runStrategyDecide(
        profile: NpcProfile,
        session: Session,
        round: Round,
        npc: User,
        ctx: DecisionCtx,
    ): Boolean = try {
        strategyOf(profile).decide(ctx)
    } catch (e: Exception) {
        logger.error("NPC strategy decide failed for user={}, fallback FAIR", npc.id, e)
        domainEventLogger.emit(
            NpcStrategyFailed(
                sessionId = session.id!!,
                roundId = round.id!!,
                userId = npc.id!!,
                strategy = profile.strategy.name,
                phase = "decide",
            )
        )
        FairStrategy(NpcParams.Fair()).decide(ctx)
    }

    private fun strategyOf(profile: NpcProfile): NpcStrategyPlayer = when (profile.strategy) {
        NpcStrategy.FAIR -> FairStrategy(profile.params as NpcParams.Fair)
        NpcStrategy.SELFISH -> SelfishStrategy(profile.params as NpcParams.Selfish)
        NpcStrategy.RANDOM -> RandomStrategy(profile.params as NpcParams.Random)
        NpcStrategy.VENGEFUL -> VengefulStrategy(profile.params as NpcParams.Vengeful)
        NpcStrategy.ADAPTIVE -> AdaptiveStrategy(profile.params as NpcParams.Adaptive)
    }

    private fun buildOfferCtx(session: Session, round: Round, me: User, profile: NpcProfile): OfferCtx =
        OfferCtx(session, round, me, pastRoundsFor(session, me), randomFor(profile, round, PHASE_OFFER))

    private fun buildDecisionCtx(session: Session, round: Round, me: User, incoming: Offer, profile: NpcProfile): DecisionCtx =
        DecisionCtx(session, round, me, incoming, pastRoundsFor(session, me), randomFor(profile, round, PHASE_DECIDE))

    private fun pastRoundsFor(session: Session, me: User): List<RoundOutcome> {
        val currentRoundNumber = session.currentRound?.roundNumber ?: Int.MAX_VALUE
        return session.rounds
            .filter { it.roundNumber < currentRoundNumber }
            .sortedBy { it.roundNumber }
            .map { r -> outcomeFor(r, me) }
    }

    private fun outcomeFor(r: Round, me: User): RoundOutcome {
        val myOffer = r.offers.find { it.proposer?.id == me.id }
        val myOfferDecision = myOffer?.let { off -> r.decisions.find { it.offer?.id == off.id } }
        val incoming = r.offers.find { it.responder?.id == me.id }
        val incomingDecision = incoming?.let { off -> r.decisions.find { it.offer?.id == off.id } }
        return RoundOutcome(
            roundNumber = r.roundNumber,
            myOfferAmount = myOffer?.offerValue,
            myOfferAccepted = myOfferDecision?.decision,
            incomingOfferAmount = incoming?.offerValue,
            incomingAccepted = incomingDecision?.decision,
        )
    }

    private fun randomFor(profile: NpcProfile, round: Round, phaseTag: Long): Random {
        val seed = profile.seed ?: return Random()
        val roundMsb = round.id?.mostSignificantBits ?: 0L
        return Random(seed xor roundMsb xor phaseTag)
    }

    private companion object {
        const val PHASE_OFFER = 0L
        const val PHASE_DECIDE = 1L
    }
}
