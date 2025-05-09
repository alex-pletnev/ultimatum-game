package edu.itmo.ultimatum_game.model

import com.fasterxml.jackson.annotation.JsonCreator

enum class RoundPhase {
    CREATED,
    OFFER_FORMS_SENT,
    WAIT_OFFERS,
    ALL_OFFERS_RECEIVED,

    //shuffle

    OFFERS_SENT,
    WAIT_DECISIONS,
    ALL_DECISIONS_RECEIVED,
    FINISHED;

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