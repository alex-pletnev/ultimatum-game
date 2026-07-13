package edu.itmo.ultimatumgame.dto.responses

import java.util.UUID

/**
 * Публикуется в /topic/session/{sessionId}/offersShuffled после того как shuffle-стратегия
 * назначила responder'а каждому офферу раунда (переход ALL_OFFERS_RECEIVED → OFFERS_SENT).
 * Позволяет фронту визуализировать pairing без ожидания персональной доставки каждому respondent'у.
 */
data class OffersShuffledResponse(
    val roundNumber: Int,
    val assignments: List<OfferAssignment>,
)

data class OfferAssignment(
    val offerId: UUID,
    val proposerId: UUID,
    val responderId: UUID,
)
