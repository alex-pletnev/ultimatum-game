package edu.itmo.ultimatum_game.controllers.ws

import edu.itmo.ultimatum_game.services.AdminGameplayService
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
    private val adminGameplayService: AdminGameplayService
) {

    private val logger = logger()

    @PreAuthorize("hasRole('ADMIN')")
    @MessageMapping("session/{sessionId}/start")
    @Transactional
    fun startSession(
        @DestinationVariable sessionId: String,
        principal: Principal
    ) {
        logger.info("получена запрос на session/${sessionId}/start от $principal")
        val sessionUuid = sessionId.toUuidOrThrow()
        adminGameplayService.startSession(sessionUuid)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MessageMapping("session/{sessionId}/close")
    @Transactional
    fun closeSession(
        @DestinationVariable sessionId: String,
        principal: Principal
    ) {
        logger.info("получена запрос на session/{sessionId}/close от $principal")
        val sessionUuid = sessionId.toUuidOrThrow()
        adminGameplayService.closeSession(sessionUuid)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MessageMapping("session/{sessionId}/open")
    @Transactional
    fun openSession(
        @DestinationVariable sessionId: String,
        principal: Principal
    ) {
        logger.info("получена запрос на session/{sessionId}/open от $principal")
        val sessionUuid = sessionId.toUuidOrThrow()
        adminGameplayService.openSession(sessionUuid)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @MessageMapping("session/{sessionId}/round.start")
    @Transactional
    fun startNextRound(
        @DestinationVariable sessionId: String,
        principal: Principal
    ) {
        logger.info("получена запрос на session/{sessionId}/round.start от $principal")
        val sessionUuid = sessionId.toUuidOrThrow()
        adminGameplayService.startNextRound(sessionUuid)
    }


}