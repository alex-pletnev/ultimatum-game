package edu.itmo.ultimatum_game.controllers.ws

import edu.itmo.ultimatum_game.services.AdminGameplayService
import edu.itmo.ultimatum_game.util.logger
import edu.itmo.ultimatum_game.util.toUuidOrThrow
import io.github.springwolf.bindings.stomp.annotations.StompAsyncOperationBinding
import io.github.springwolf.core.asyncapi.annotations.AsyncListener
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation
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

    @AsyncListener(
        operation = AsyncOperation(
            channelName = "/app/session/{sessionId}/start",
            description = "ADMIN: старт сессии. Пустой payload.",
            payloadType = String::class
        )
    )
    @StompAsyncOperationBinding
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

    @AsyncListener(
        operation = AsyncOperation(
            channelName = "/app/session/{sessionId}/close",
            description = "ADMIN: закрытие сессии (openToConnect=false + завершение). Пустой payload.",
            payloadType = String::class
        )
    )
    @StompAsyncOperationBinding
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

    @AsyncListener(
        operation = AsyncOperation(
            channelName = "/app/session/{sessionId}/open",
            description = "ADMIN: открыть сессию для подключений (openToConnect=true). Пустой payload.",
            payloadType = String::class
        )
    )
    @StompAsyncOperationBinding
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

    @AsyncListener(
        operation = AsyncOperation(
            channelName = "/app/session/{sessionId}/round.start",
            description = "ADMIN: старт следующего раунда сессии. Пустой payload.",
            payloadType = String::class
        )
    )
    @StompAsyncOperationBinding
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