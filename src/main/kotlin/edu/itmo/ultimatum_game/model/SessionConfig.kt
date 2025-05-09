package edu.itmo.ultimatum_game.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Embeddable
data class SessionConfig(

    @field:Column(nullable = false)
    @field:Enumerated(EnumType.STRING)
    val sessionType: SessionType,
    @field:Column(nullable = false)
    val numRounds: Int,
    @field:Column(nullable = false)
    //если игра не по командам, то значение 0
    val numTeams: Int,
    @field:Column(nullable = false)
    val numPlayers: Int,
    @field:Column(nullable = false)
    val roundSum: Int,
    @field:Column(nullable = false)
    val timeoutMoveSec: Int,
)
