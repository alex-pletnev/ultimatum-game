package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.repositories.SessionRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import java.util.UUID

@Service("sessionAccess")
class SessionAccess(private val sessions: SessionRepository) {
    fun isHost(sessionId: String, authentication: Authentication): Boolean {
        val id = runCatching { UUID.fromString(sessionId) }.getOrNull()
        val userId = runCatching { UUID.fromString(authentication.name) }.getOrNull()
        return id != null && userId != null && sessions.existsByIdAndAdminId(id, userId)
    }
}
