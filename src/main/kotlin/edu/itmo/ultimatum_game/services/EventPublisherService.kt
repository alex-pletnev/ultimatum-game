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

    fun publishOfferCreated(sessionId: UUID, offer: Offer) {
        val dto = offerMapper.toDto(offer)
        val destination = "/topic/session/${sessionId}/offerCreated"
        logger.info("Публикация события OfferCreated в $destination для offer=$dto")
        messagingTemplate.convertAndSend(destination, dto)
    }

    fun publishDecisionMade(sessionId: UUID, decision: Decision) {
        val dto = decisionMapper.toDto(decision)
        val destination = "/topic/session/${sessionId}/decisionMade"
        logger.info("Публикация события DecisionMade в $destination для decision=$dto")
        messagingTemplate.convertAndSend(destination, dto)
    }



    fun publishRoundStatus(sessionId: UUID, round: Round) {
        logger.info("Entity раунда для публикации {}", round)
        val dto = roundMapper.toDto(round)
        logger.info("Dto раунда для публикации {}", dto)
        val destination = "/topic/session/${sessionId}/roundStatus"
        logger.info("Публикация события RoundStatus в $destination для раунда #${dto.roundNumber} (phase=${dto.roundPhase})")
        messagingTemplate.convertAndSend(destination, dto)
    }

}