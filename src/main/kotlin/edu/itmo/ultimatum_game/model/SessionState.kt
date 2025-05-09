package edu.itmo.ultimatum_game.model

import com.fasterxml.jackson.annotation.JsonCreator

enum class SessionState {
    CREATED,
    RUNNING,
    FINISHED,
    ABORTED;

    companion object {
        @JvmStatic
        @JsonCreator
        fun from(value: String): SessionState =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Недопустимое значение для SessionState: $value. " +
                            "Допустимые значения: ${entries.joinToString()}"
                )
    }
}