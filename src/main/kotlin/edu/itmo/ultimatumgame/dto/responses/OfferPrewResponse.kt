package edu.itmo.ultimatumgame.dto.responses

import java.util.Date
import java.util.UUID

/**
 * DTO for {@link edu.itmo.ultimatumgame.model.Offer}.
 *
 * T-073: defaults убраны из первичного конструктора. Раньше все поля были nullable
 * с `= null` — Kotlin из-за этого генерил no-arg конструктор, а MapStruct 1.6.3 для
 * такого случая выбирает его как "canonical" и не мапит ни одно поле → `toDto` отдавал
 * пустой DTO с `id/proposer/responder/offerValue = null`, что фронт видел как «оффер
 * без данных». Без defaults MapStruct вынужден использовать primary constructor и
 * заполняет всё корректно.
 */
data class OfferPrewResponse(
    val id: UUID?,
    val proposer: UserResponse?,
    val responder: UserResponse?,
    val offerValue: Int?,
    val createdAt: Date?,
)
