@file:Suppress("UnsafeCallOnNullableType", "UnnecessaryNotNullOperator")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.responses.OfferAssignment
import edu.itmo.ultimatumgame.dto.responses.OffersShuffledResponse
import edu.itmo.ultimatumgame.model.RoundPhase
import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.OfferShuffled
import edu.itmo.ultimatumgame.util.logger
import edu.itmo.ultimatumgame.util.withRoundMdc
import edu.itmo.ultimatumgame.util.withSessionMdc
import org.springframework.stereotype.Service

@Service
class CoreGameplayService(
    private val eventPublisherService: EventPublisherService,
    private val domainEventLogger: DomainEventLogger,
) {

    private val logger = logger()

    fun initWaitDecisionsPhase(session: Session) {
        val sessionId = requireNotNull(session.id) {
            "session.id is null after shuffle — сессия должна быть persisted"
        }
        withSessionMdc(sessionId) {
            logger.info("Вызван метод initWaitDecisionsPhase для сессии {}", session.id)
            logger.debug("Перемешиваю офферы по стратегии для сессии {}", session.id)
            session.config!!.sessionType.shuffleStrategy.shuffleOffers(session)
            logger.info("Офферы перемешаны для сессии {}", session.id)

            val round = session.currentRound
                ?: error("session.currentRound не должен быть null на данном этапе")
            withRoundMdc(round.id!!) {
                dispatchOffers(session, sessionId, round)
                broadcastShuffleAssignments(sessionId, round)
                round.roundPhase = RoundPhase.OFFERS_SENT
                logger.info(
                    "Установлена фаза раунда {}: {} для сессии {}",
                    round.roundNumber,
                    round.roundPhase,
                    session.id,
                )
            }
        }
    }

    private fun broadcastShuffleAssignments(
        sessionId: java.util.UUID,
        round: edu.itmo.ultimatumgame.model.Round,
    ) {
        val assignments = round.offers.mapNotNull { offer ->
            val responderId = offer.responder?.id ?: return@mapNotNull null
            OfferAssignment(
                offerId = offer.id!!,
                proposerId = offer.proposer!!.id!!,
                responderId = responderId,
            )
        }
        eventPublisherService.publishOffersShuffled(
            sessionId,
            OffersShuffledResponse(
                roundNumber = round.roundNumber,
                assignments = assignments,
            )
        )
    }

    private fun dispatchOffers(
        session: Session,
        sessionId: java.util.UUID,
        round: edu.itmo.ultimatumgame.model.Round,
    ) {
        logger.debug("Отправляю офферы игрокам для раунда {} сессии {}", round.roundNumber, session.id)
        round.offers.forEach { offer ->
            val responderId = offer.responder?.id
            if (responderId == null) {
                logger.warn("Оффер {} без respondera — публикация пропущена (баг данных)", offer.id)
                return@forEach
            }
            logger.info(
                "Публикую оффер {} игроку {} в раунде {} сессии {}",
                offer.id,
                responderId,
                round.roundNumber,
                sessionId,
            )
            eventPublisherService.publishOfferToPlayer(sessionId, responderId, offer)
            domainEventLogger.emit(
                OfferShuffled(
                    sessionId = sessionId,
                    roundId = round.id!!,
                    offerId = offer.id!!,
                    proposerId = offer.proposer!!.id!!,
                    responderId = responderId,
                )
            )
        }
    }
}
