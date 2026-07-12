package edu.itmo.ultimatum_game.controllers.ws

import edu.itmo.ultimatum_game.dto.requests.CreateOfferCmd
import edu.itmo.ultimatum_game.dto.requests.MakeDecisionCmd
import edu.itmo.ultimatum_game.services.PlayerGameplayService
import edu.itmo.ultimatum_game.util.logger
import edu.itmo.ultimatum_game.util.toUuidOrThrow
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding
import io.github.springwolf.core.asyncapi.annotations.AsyncListener
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation
import jakarta.validation.Valid
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import java.security.Principal
import java.util.*

@Controller
class OfferWsController(
    private val gameplayService: PlayerGameplayService,
) {

    private val logger = logger()

    @AsyncListener(
        operation = AsyncOperation(
            channelName = "/app/session/{sessionId}/offer.create",
            description = "PLAYER/ADMIN: отправить оффер респонденту. Валидируется amount>=0.",
            payloadType = CreateOfferCmd::class
        )
    )
    @StompAsyncOperationBinding
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    @MessageMapping("session/{sessionId}/offer.create")
    fun createOffer(
        @DestinationVariable sessionId: String,
        @Valid @Payload cmd: CreateOfferCmd,
        principal: Principal
    ) {
        logger.info("получена запрос на session/{sessionId}/offer.create от $principal c payload: $cmd")
        val sessionUuid: UUID = sessionId.toUuidOrThrow()
        val playerUuid: UUID = principal.name.toUuidOrThrow()
        gameplayService.sendOffer(sessionUuid, playerUuid, cmd)
    }

    @AsyncListener(
        operation = AsyncOperation(
            channelName = "/app/session/{sessionId}/make.decision",
            description = "PLAYER/ADMIN: решение респондента (accept/reject) по конкретному офферу.",
            payloadType = MakeDecisionCmd::class
        )
    )
    @StompAsyncOperationBinding
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    @MessageMapping("session/{sessionId}/make.decision")
    fun makeDecision(
        @DestinationVariable sessionId: String,
        @Valid @Payload cmd: MakeDecisionCmd,
        principal: Principal
    ) {
        logger.info("получена запрос на session/{sessionId}/make.decision от $principal c payload: $cmd")
        val sessionUuid: UUID = sessionId.toUuidOrThrow()
        val playerUuid: UUID = principal.name.toUuidOrThrow()
        gameplayService.makeDecision(sessionUuid, playerUuid, cmd)
    }

}