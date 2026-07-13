@file:Suppress("MaxLineLength", "MaximumLineLength")

package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.model.SessionType
import java.util.UUID

/**
 * Типизированные доменные события — единственный «жёсткий» канал логирования.
 * Каждый экземпляр порождает одну запись в структурированном логе + инкремент
 * Micrometer counter'а. См. [DomainEventLogger].
 *
 * Не использовать SLF4J напрямую для доменных событий — только через [DomainEventLogger.emit].
 */
sealed interface DomainEvent {
    /** Уникальный dotted-тип, попадает в поле event_type лога и в имя counter'а. */
    val type: String

    /** Поля, попадающие в структурированный лог как key-value пары. Не должны содержать null-значений в значащих ключах. */
    val fields: Map<String, Any?>
}

// ----- auth -----

data class AuthRegister(val userId: UUID, val nickname: String, val role: Role) : DomainEvent {
    override val type = "auth.register"
    override val fields = mapOf("userId" to userId, "nickname" to nickname, "role" to role.name)
}

data class AuthLogin(val userId: UUID) : DomainEvent {
    override val type = "auth.login"
    override val fields = mapOf("userId" to userId)
}

// ----- session lifecycle -----

data class SessionCreated(val sessionId: UUID, val adminId: UUID, val sessionType: SessionType) : DomainEvent {
    override val type = "session.created"
    override val fields = mapOf("sessionId" to sessionId, "adminId" to adminId, "sessionType" to sessionType.name)
}

data class SessionStarted(val sessionId: UUID, val playerCount: Int) : DomainEvent {
    override val type = "session.started"
    override val fields = mapOf("sessionId" to sessionId, "playerCount" to playerCount)
}

data class SessionClosed(val sessionId: UUID) : DomainEvent {
    override val type = "session.closed"
    override val fields = mapOf("sessionId" to sessionId)
}

data class SessionAborted(val sessionId: UUID) : DomainEvent {
    override val type = "session.aborted"
    override val fields = mapOf("sessionId" to sessionId)
}

// ----- membership -----

data class PlayerJoined(val sessionId: UUID, val userId: UUID, val role: Role) : DomainEvent {
    override val type = "player.joined"
    override val fields = mapOf("sessionId" to sessionId, "userId" to userId, "role" to role.name)
}

data class PlayerLeft(val sessionId: UUID, val userId: UUID) : DomainEvent {
    override val type = "player.left"
    override val fields = mapOf("sessionId" to sessionId, "userId" to userId)
}

// ----- round lifecycle -----

data class RoundStarted(val sessionId: UUID, val roundId: UUID, val roundNumber: Int) : DomainEvent {
    override val type = "round.started"
    override val fields = mapOf("sessionId" to sessionId, "roundId" to roundId, "roundNumber" to roundNumber)
}

data class RoundClosed(val sessionId: UUID, val roundId: UUID, val roundNumber: Int) : DomainEvent {
    override val type = "round.closed"
    override val fields = mapOf("sessionId" to sessionId, "roundId" to roundId, "roundNumber" to roundNumber)
}

// ----- gameplay -----

data class OfferSubmitted(
    val sessionId: UUID,
    val roundId: UUID,
    val offerId: UUID,
    val proposerId: UUID,
    val amount: Int
) : DomainEvent {
    override val type = "offer.submitted"
    override val fields = mapOf(
        "sessionId" to sessionId,
        "roundId" to roundId,
        "offerId" to offerId,
        "proposerId" to proposerId,
        "amount" to amount
    )
}

data class OfferShuffled(
    val sessionId: UUID,
    val roundId: UUID,
    val offerId: UUID,
    val proposerId: UUID,
    val responderId: UUID
) : DomainEvent {
    override val type = "offer.shuffled"
    override val fields = mapOf(
        "sessionId" to sessionId,
        "roundId" to roundId,
        "offerId" to offerId,
        "proposerId" to proposerId,
        "responderId" to responderId
    )
}

data class DecisionMade(
    val sessionId: UUID,
    val roundId: UUID,
    val offerId: UUID,
    val responderId: UUID,
    val accepted: Boolean,
    val amount: Int
) : DomainEvent {
    override val type = "decision.made"
    override val fields = mapOf(
        "sessionId" to sessionId,
        "roundId" to roundId,
        "offerId" to offerId,
        "responderId" to responderId,
        "accepted" to accepted,
        "amount" to amount
    )
}
