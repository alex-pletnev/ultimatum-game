package edu.itmo.ultimatum_game.model

import com.fasterxml.jackson.annotation.JsonCreator

enum class SessionType(val shuffleStrategy: ShuffleStrategy) {
    FREE_FOR_ALL(FreeForAllStrategy()),
    TEAM_BATTLE(TeamBattleStrategy());

    companion object {
        @JvmStatic
        @JsonCreator
        fun from(value: String): SessionType =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Недопустимое значение для SessionType: $value. " +
                            "Допустимые значения: ${entries.joinToString()}"
                )
    }
}