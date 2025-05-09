package edu.itmo.ultimatum_game.model

import com.fasterxml.jackson.annotation.JsonCreator

enum class Role {
    ADMIN,
    PLAYER,
    OBSERVER,
    NPC;

    companion object {
        @JvmStatic
        @JsonCreator
        fun from(value: String): Role =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Недопустимое значение для Role: $value. " +
                            "Допустимые значения: ${entries.joinToString()}"
                )
    }

}