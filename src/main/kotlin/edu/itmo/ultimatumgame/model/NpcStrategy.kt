package edu.itmo.ultimatumgame.model

import com.fasterxml.jackson.annotation.JsonCreator

enum class NpcStrategy {
    FAIR,
    SELFISH,
    RANDOM,
    VENGEFUL,
    ADAPTIVE;

    companion object {
        @JvmStatic
        @JsonCreator
        fun from(value: String): NpcStrategy =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Недопустимое значение для NpcStrategy: $value. " +
                        "Допустимые значения: ${entries.joinToString()}"
                )
    }
}
