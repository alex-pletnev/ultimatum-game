package edu.itmo.ultimatumgame.dto.responses

import java.util.Date
import java.util.UUID

/**
 * Payload персональной доставки оффера респонденту (T-058).
 *
 * Приходит по destination `/topic/session/{sessionId}/player/{userId}/offer` — где `{userId}` —
 * respondent'а. Семантика: «этот оффер адресован тебе, прими решение accept/reject».
 *
 * Отличается от [OfferCreatedResponse] (broadcast) явной семантикой assignment'а: клиенту
 * не нужно сравнивать responder.id со своим userId, чтобы понять «мне или нет» — сам факт
 * прихода payload'а на персональный topic означает «тебе».
 */
data class AssignedOfferResponse(
    val offerId: UUID,
    val round: RoundPrewResponse,
    val proposer: UserResponse,
    val amount: Int,
    val offeredAt: Date,
)
