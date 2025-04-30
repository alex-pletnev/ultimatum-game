package edu.itmo.ultimatum_game.model

enum class SessionType(val shuffleStrategy: ShuffleStrategy) {
    FREE_FOR_ALL(FreeForAllStrategy()),
    TEAM_BATTLE(TeamBattleStrategy()),
}