package edu.itmo.ultimatum_game.controllers.ws

import edu.itmo.ultimatum_game.dto.requests.CreateOfferCmd
import edu.itmo.ultimatum_game.services.PlayerGameplayService
import edu.itmo.ultimatum_game.util.logger
import edu.itmo.ultimatum_game.util.toUuidOrThrow
import jakarta.validation.Valid
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller
import java.security.Principal
import java.util.*

@Controller
class OfferWsController(
    private val gameplayService: PlayerGameplayService,
) {

    private val logger = logger()

    @MessageMapping("session.{sessionId}.offer.create")
    fun createOffer(
        @DestinationVariable sessionId: String,
        @Valid @Payload cmd: CreateOfferCmd,
        principal: Principal
    ) {
        val sessionUuid: UUID = sessionId.toUuidOrThrow()
        val proposerUuid: UUID = principal.name.toUuidOrThrow()


    }

}