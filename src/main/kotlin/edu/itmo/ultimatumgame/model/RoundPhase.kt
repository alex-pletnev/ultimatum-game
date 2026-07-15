package edu.itmo.ultimatumgame.model

import com.fasterxml.jackson.annotation.JsonCreator

enum class RoundPhase {
    CREATED,
    WAIT_OFFERS,
    ALL_OFFERS_RECEIVED,
    OFFERS_SENT,
    ALL_DECISIONS_RECEIVED,
    FINISHED,

    // Раунд принудительно прерван админом до нормального завершения (T-054).
    // Дальнейшие offers/decisions игнорируются; startNextRound переводит в следующий раунд.
    ABORTED;

    companion object {
        @JvmStatic
        @JsonCreator
        fun from(value: String): RoundPhase =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Недопустимое значение для RoundPhase: $value. " +
                        "Допустимые значения: ${entries.joinToString()}"
                )
    }
}
