package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.model.RoundPhase
import edu.itmo.ultimatum_game.model.Session
import edu.itmo.ultimatum_game.util.logger
import org.springframework.stereotype.Service

@Service
class CoreGameplayService(
    private val eventPublisherService: EventPublisherService
) {

    private val logger = logger()

    fun initWaitDecisionsPhase(session: Session) {
        logger.info("Вызван метод initWaitDecisionsPhase для сессии {}", session.id)
        logger.debug("Перемешиваю офферы по стратегии для сессии {}", session.id)
        session.config!!.sessionType.shuffleStrategy.shuffleOffers(session)
        logger.info("Офферы перемешаны для сессии {}", session.id)

        val round = session.currentRound ?: error("session.currentRound не должен быть null на данном этапе")
        logger.debug("Отправляю офферы игрокам для раунда {} сессии {}", round.roundNumber, session.id)
        val sessionId = requireNotNull(session.id) { "session.id is null after shuffle — сессия должна быть persisted" }
        round.offers.forEach { offer ->
            val responderId = offer.responder?.id
            if (responderId == null) {
                logger.warn("Оффер {} без respondera — публикация пропущена (баг данных)", offer.id)
                return@forEach
            }
            logger.info("Публикую оффер {} игроку {} в раунде {} сессии {}", offer.id, responderId, round.roundNumber, sessionId)
            eventPublisherService.publishOfferToPlayer(sessionId, responderId, offer)
        }

        round.roundPhase = RoundPhase.OFFERS_SENT
        logger.info("Установлена фаза раунда {}: {} для сессии {}", round.roundNumber, round.roundPhase, session.id)
    }
}
