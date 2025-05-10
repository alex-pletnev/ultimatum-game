package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.util.logger
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AdminGameplayService(
    private val sessionService: SessionService,
    private val userService: UserService,
) {

    private val logger = logger()

    @Transactional
    fun startSession(sessionId: UUID) {
        val user = userService.getCurrentUser()
        sessionService.setSessionState()
    }

    fun closeSession(){TODO()}

    fun openSession(){TODO()}

    fun abortSession(){TODO()}

    fun startNextRound(){TODO()}

    fun abortCurrentRound(){TODO()}

    fun pauseRound(){TODO("бонусом")}



}