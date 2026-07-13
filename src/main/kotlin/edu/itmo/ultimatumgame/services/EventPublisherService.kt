@file:Suppress("MaxLineLength", "MaximumLineLength")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.responses.DecisionMadeResponse
import edu.itmo.ultimatumgame.dto.responses.OfferCreatedResponse
import edu.itmo.ultimatumgame.dto.responses.RoundResponse
import edu.itmo.ultimatumgame.dto.responses.SessionScoreDto
import edu.itmo.ultimatumgame.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatumgame.model.Decision
import edu.itmo.ultimatumgame.model.Offer
import edu.itmo.ultimatumgame.model.Round
import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.util.DecisionMapper
import edu.itmo.ultimatumgame.util.OfferMapper
import edu.itmo.ultimatumgame.util.RoundMapper
import edu.itmo.ultimatumgame.util.SessionWithTeamsAndMembersMapper
import edu.itmo.ultimatumgame.util.logger
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.util.UUID

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
    fun publishOfferCreated(sessionId: UUID, offer: Offer) {
        val dto = offerMapper.toDto(offer)
        val destination = "/topic/session/$sessionId/offerCreated"
        logger.info("Публикация события OfferCreated в $destination для offer=$dto")
        messagingTemplate.convertAndSend(destination, dto)
    }

    @AsyncPublisher(
        operation = AsyncOperation(
            channelName = "/topic/session/{sessionId}/player/{userId}/offer",
            description = "Персональная доставка оффера конкретному респонденту (userId). Используется в режиме shuffle.",
            payloadType = OfferCreatedResponse::class
        )
    )
    @StompAsyncOperationBinding
    fun publishOfferToPlayer(sessionId: UUID, proposerId: UUID, offer: Offer) {
        val dto = offerMapper.toDto(offer)
        val destination = "/topic/session/$sessionId/player/$proposerId/offer"
        logger.info("Публикация события offer в $destination для offer=$dto")
        messagingTemplate.convertAndSend(destination, dto)
    }

    @AsyncPublisher(
        operation = AsyncOperation(
            channelName = "/topic/session/{sessionId}/decisionMade",
            description = "Публикуется на каждое решение (accept/reject) респондента.",
            payloadType = DecisionMadeResponse::class
        )
    )
    @StompAsyncOperationBinding
    fun publishDecisionMade(sessionId: UUID, decision: Decision) {
        val dto = decisionMapper.toDto(decision)
        val destination = "/topic/session/$sessionId/decisionMade"
        logger.info("Публикация события DecisionMade в $destination для decision=$dto")
        messagingTemplate.convertAndSend(destination, dto)
    }

    @AsyncPublisher(
        operation = AsyncOperation(
            channelName = "/topic/session/{sessionId}/roundStatus",
            description = "Публикуется при каждой смене фазы раунда (RoundPhase).",
            payloadType = RoundResponse::class
        )
    )
    @StompAsyncOperationBinding
    fun publishRoundStatus(sessionId: UUID, round: Round) {
        val dto = roundMapper.toDto(round)
        val destination = "/topic/session/$sessionId/roundStatus"
        logger.info(
            "Публикация события RoundStatus в $destination для раунда #${dto.roundNumber} (phase=${dto.roundPhase})"
        )
        messagingTemplate.convertAndSend(destination, dto)
    }

    @AsyncPublisher(
        operation = AsyncOperation(
            channelName = "/topic/session/{sessionId}/scoreUpdated",
            description = "Публикуется после закрытия раунда (ALL_DECISIONS_RECEIVED). Содержит per-player и per-team суммы баллов и roundSum.",
            payloadType = SessionScoreDto::class
        )
    )
    @StompAsyncOperationBinding
    fun publishScoreUpdated(sessionId: UUID, score: SessionScoreDto) {
        val destination = "/topic/session/$sessionId/scoreUpdated"
        logger.info(
            "Публикация события ScoreUpdated в $destination для сессии $sessionId (roundsCompleted=${score.roundsCompleted})"
        )
        messagingTemplate.convertAndSend(destination, score)
    }

    @AsyncPublisher(
        operation = AsyncOperation(
            channelName = "/topic/session/{sessionId}/sessionStatus",
            description = "Публикуется на join / start / close / open / abort сессии.",
            payloadType = SessionWithTeamsAndMembersResponse::class
        )
    )
    @StompAsyncOperationBinding
    fun publishSessionStatus(sessionId: UUID, session: Session) {
        val dto = sessionWithTeamsAndMembersMapper.toDto(session)
        val destination = "/topic/session/$sessionId/sessionStatus"
        logger.info("Публикация события SessionStatus в $destination для сессии #${dto.displayName} (id=${dto.id})")
        messagingTemplate.convertAndSend(destination, dto)
    }
}
