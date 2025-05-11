package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.model.Decision
import edu.itmo.ultimatum_game.model.Offer
import edu.itmo.ultimatum_game.model.Round
import edu.itmo.ultimatum_game.util.DecisionMapper
import edu.itmo.ultimatum_game.util.OfferMapper
import edu.itmo.ultimatum_game.util.RoundMapper
import edu.itmo.ultimatum_game.util.logger
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.util.*

@Service
class EventPublisherService(
    private val messagingTemplate: SimpMessagingTemplate,
    private val offerMapper: OfferMapper,
    private val decisionMapper: DecisionMapper,
    private val roundMapper: RoundMapper,
) {

    private val logger = logger()

    fun publishOfferCreated(sessionId: UUID?, offer: Offer?) {
        if (sessionId != null && offer != null) {
            val dto = offerMapper.toDto(offer)
            val destination = "/topic/session/${sessionId}/offerCreated"
            logger.info("Публикация события OfferCreated в $destination для offer=$dto")
            messagingTemplate.convertAndSend(destination, dto)
        } else {
            error("sessionId и offer не может быть null на этом этапе")
        }

    }

    fun publishOfferToPlayer(sessionId: UUID?, proposerId: UUID?, offer: Offer?) {
        if (sessionId != null && proposerId != null && offer != null) {
            val dto = offerMapper.toDto(offer)
            val destination = "/topic/session/${sessionId}/player/${proposerId}/offer"
            logger.info("Публикация события offer в $destination для offer=$dto")
            messagingTemplate.convertAndSend(destination, dto)
        } else {
            error("sessionId, proposerId и offer не могут быть null на этом этапе")
        }
    }

    fun publishDecisionMade(sessionId: UUID?, decision: Decision?) {
        if (sessionId != null && decision != null) {
            val dto = decisionMapper.toDto(decision)
            val destination = "/topic/session/${sessionId}/decisionMade"
            logger.info("Публикация события DecisionMade в $destination для decision=$dto")
            messagingTemplate.convertAndSend(destination, dto)
        } else {
            error("sessionId и decision не может быть null на этом этапе")
        }
    }

    fun publishRoundStatus(sessionId: UUID?, round: Round?) {
        if (round != null && sessionId != null) {
            logger.info("Entity раунда для публикации {}", round)
            val dto = roundMapper.toDto(round)
            logger.info("Dto раунда для публикации {}", dto)
            val destination = "/topic/session/${sessionId}/roundStatus"
            logger.info("Публикация события RoundStatus в $destination для раунда #${dto.roundNumber} (phase=${dto.roundPhase})")
            messagingTemplate.convertAndSend(destination, dto)
        } else {
            error("sessionId и round не может быть null на этом этапе")
        }

    }

}