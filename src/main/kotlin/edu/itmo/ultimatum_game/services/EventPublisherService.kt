package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.dto.responses.DecisionMadeResponse
import edu.itmo.ultimatum_game.dto.responses.OfferCreatedResponse
import edu.itmo.ultimatum_game.dto.responses.RoundResponse
import edu.itmo.ultimatum_game.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatum_game.model.Decision
import edu.itmo.ultimatum_game.model.Offer
import edu.itmo.ultimatum_game.model.Round
import edu.itmo.ultimatum_game.model.Session
import edu.itmo.ultimatum_game.util.*
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.util.*

@Service
class EventPublisherService(
    @Lazy private val messagingTemplate: SimpMessagingTemplate,
    private val offerMapper: OfferMapper,
    private val decisionMapper: DecisionMapper,
    private val roundMapper: RoundMapper,
    private val sessionWithTeamsAndMembersMapper: SessionWithTeamsAndMembersMapper
) {

    private val logger = logger()

    @AsyncPublisher(
        operation = AsyncOperation(
            channelName = "/topic/session/{sessionId}/offerCreated",
            description = "Публикуется на каждый новый оффер в раунде. Broadcast всем подписчикам сессии.",
            payloadType = OfferCreatedResponse::class
        )
    )
    @StompAsyncOperationBinding
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

    @AsyncPublisher(
        operation = AsyncOperation(
            channelName = "/topic/session/{sessionId}/player/{userId}/offer",
            description = "Персональная доставка оффера конкретному респонденту (userId). Используется в режиме shuffle.",
            payloadType = OfferCreatedResponse::class
        )
    )
    @StompAsyncOperationBinding
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

    @AsyncPublisher(
        operation = AsyncOperation(
            channelName = "/topic/session/{sessionId}/decisionMade",
            description = "Публикуется на каждое решение (accept/reject) респондента.",
            payloadType = DecisionMadeResponse::class
        )
    )
    @StompAsyncOperationBinding
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

    @AsyncPublisher(
        operation = AsyncOperation(
            channelName = "/topic/session/{sessionId}/roundStatus",
            description = "Публикуется при каждой смене фазы раунда (RoundPhase).",
            payloadType = RoundResponse::class
        )
    )
    @StompAsyncOperationBinding
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

    @AsyncPublisher(
        operation = AsyncOperation(
            channelName = "/topic/session/{sessionId}/sessionStatus",
            description = "Публикуется на join / start / close / open / abort сессии.",
            payloadType = SessionWithTeamsAndMembersResponse::class
        )
    )
    @StompAsyncOperationBinding
    fun publishSessionStatus(sessionId: UUID?, session: Session?) {
        if (session != null && sessionId != null) {
            logger.info("Entity сесии для публикации {}", session)
            val dto = sessionWithTeamsAndMembersMapper.toDto(session)
            logger.info("Dto сессии для публикации {}", dto)
            val destination = "/topic/session/${sessionId}/sessionStatus"
            logger.info("Публикация события SessionStatus в $destination для раунда сессии #${dto.displayName} (id=${dto.id})")
            messagingTemplate.convertAndSend(destination, dto)
        } else {
            error("sessionId и round не может быть null на этом этапе")
        }
    }

}