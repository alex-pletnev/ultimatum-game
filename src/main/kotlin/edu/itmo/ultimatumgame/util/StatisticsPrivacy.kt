package edu.itmo.ultimatumgame.util

import edu.itmo.ultimatumgame.dto.responses.OfferStatsDto
import edu.itmo.ultimatumgame.dto.responses.SessionScoreDto
import edu.itmo.ultimatumgame.dto.responses.UserInfo
import edu.itmo.ultimatumgame.model.Session

fun OfferStatsDto.anonymized(session: Session): OfferStatsDto {
    if (session.config?.anonymous != true) return this
    val proposerId = session.publicPlayerId(proposer.id)
    val responderId = responder?.let { session.publicPlayerId(it.id) }
    return copy(
        proposer = UserInfo(proposerId, playerAlias(proposerId)),
        responder = responderId?.let { UserInfo(it, playerAlias(it)) },
    )
}

fun SessionScoreDto.anonymized(session: Session): SessionScoreDto {
    if (session.config?.anonymous != true) return this
    return copy(
        players = players.map {
            val aliasId = session.publicPlayerId(it.userId)
            it.copy(userId = aliasId, nickname = playerAlias(aliasId))
        }
    )
}
