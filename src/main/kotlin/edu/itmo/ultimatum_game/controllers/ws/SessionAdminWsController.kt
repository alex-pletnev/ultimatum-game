package edu.itmo.ultimatum_game.controllers.ws

import edu.itmo.ultimatum_game.services.EventPublisherService
import edu.itmo.ultimatum_game.services.SessionService
import edu.itmo.ultimatum_game.util.logger
import edu.itmo.ultimatum_game.util.toUuidOrThrow
import jakarta.transaction.Transactional
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class SessionAdminWsController(
    private val eventPublisherService: EventPublisherService,
    private val sessionService: SessionService
) {

    private val logger = logger()

    @PreAuthorize("hasRole('ADMIN')")
    @MessageMapping("session/{sessionId}/round.start")
    @Transactional
    fun startSession(
        @DestinationVariable sessionId: String,
        principal: Principal
    ) {
        logger.info("получена запрос на session/${sessionId}/round.start от $principal")

        //TODO now it's tmp
        val uuid = sessionId.toUuidOrThrow()
        val session = sessionService.getSessionEntity(uuid)
        session.currentRound = session.rounds[0]
        eventPublisherService.publishRoundStatus(uuid, session.currentRound!!)

    }

}