package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.AssignedOfferResponse
import edu.itmo.ultimatumgame.dto.responses.DecisionMadeResponse
import edu.itmo.ultimatumgame.dto.responses.DecisionPrewResponse
import edu.itmo.ultimatumgame.dto.responses.OfferCreatedResponse
import edu.itmo.ultimatumgame.dto.responses.OfferPrewResponse
import edu.itmo.ultimatumgame.dto.responses.RoundResponse
import edu.itmo.ultimatumgame.dto.responses.UserResponse
import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.model.Session
import java.util.Date
import java.util.UUID

fun Session.publicPlayerId(userId: UUID): UUID = if (config?.anonymous == true) {
    // A per-game secret prevents linking public roster IDs to anonymous decisions.
    UUID.nameUUIDFromBytes("$anonymityKey:$userId".toByteArray(Charsets.UTF_8))
} else {
    userId
}

private const val ALIAS_LENGTH = 6

internal fun playerAlias(id: UUID): String = "Игрок ${id.toString().take(ALIAS_LENGTH)}"

private fun UserResponse.anonymized(session: Session?): UserResponse {
    if (session?.config?.anonymous != true) return this
    val aliasId = session.publicPlayerId(id)
    return copy(id = aliasId, nickname = playerAlias(aliasId), role = Role.PLAYER, createdAt = Date(0))
}

private fun OfferPrewResponse.anonymized(session: Session?): OfferPrewResponse = copy(
    proposer = proposer?.anonymized(session),
    responder = responder?.anonymized(session),
).also { it.automatic = automatic }

private fun DecisionPrewResponse.anonymized(session: Session?): DecisionPrewResponse = copy(
    responder = responder?.anonymized(session),
    offer = offer?.anonymized(session),
).also { it.automatic = automatic }

fun RoundResponse.anonymized(session: Session?): RoundResponse {
    if (session?.config?.anonymous != true) return this
    return copy(
        offers = offers.map { it.anonymized(session) }.toMutableList(),
        decisions = decisions.map { it.anonymized(session) }.toMutableList(),
    ).also {
        it.myRole = myRole
        it.myPendingActions = myPendingActions
    }
}

fun OfferCreatedResponse.anonymized(session: Session?): OfferCreatedResponse {
    if (session?.config?.anonymous != true) return this
    return copy(
        proposer = proposer.anonymized(session),
        responder = responder?.anonymized(session),
    )
}

fun AssignedOfferResponse.anonymized(session: Session?): AssignedOfferResponse {
    if (session?.config?.anonymous != true) return this
    return copy(
        proposer = proposer.anonymized(session),
    )
}

fun DecisionMadeResponse.anonymized(session: Session?): DecisionMadeResponse {
    if (session?.config?.anonymous != true) return this
    return copy(
        responder = responder.anonymized(session),
        offer = offer.anonymized(session),
    )
}
