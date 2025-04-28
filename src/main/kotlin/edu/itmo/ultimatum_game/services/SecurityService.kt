package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.util.logger
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.util.*

@Service
class SecurityService {

    private val logger = logger()


    fun getCurrentUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
        val userId = UUID.fromString(authentication.name)
        logger.info("Текущий пользователь с id=$userId получен из контекста безопасности")
        return userId
    }
}