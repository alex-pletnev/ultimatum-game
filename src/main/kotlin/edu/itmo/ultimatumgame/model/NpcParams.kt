package edu.itmo.ultimatumgame.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = NpcParams.Fair::class, name = "FAIR"),
    JsonSubTypes.Type(value = NpcParams.Selfish::class, name = "SELFISH"),
    JsonSubTypes.Type(value = NpcParams.Random::class, name = "RANDOM"),
    JsonSubTypes.Type(value = NpcParams.Vengeful::class, name = "VENGEFUL"),
    JsonSubTypes.Type(value = NpcParams.Adaptive::class, name = "ADAPTIVE"),
)
sealed interface NpcParams {
    data class Fair(val fairnessThreshold: Double = 0.30) : NpcParams
    data class Selfish(val minOffer: Int = 0) : NpcParams
    data class Random(val acceptProbability: Double = 0.5) : NpcParams
    data class Vengeful(
        val baselineFraction: Double = 0.5,
        val punishStep: Int = 1,
        val fairnessThreshold: Double = 0.30,
    ) : NpcParams
    data class Adaptive(
        val baselineFraction: Double = 0.5,
        val targetRejectRate: Double = 0.2,
        val slope: Double = 0.5,
    ) : NpcParams
}
