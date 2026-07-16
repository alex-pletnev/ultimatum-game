package edu.itmo.ultimatumgame.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals

class NpcParamsJacksonTest {

    private val mapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `round-trip Fair`() {
        val original: NpcParams = NpcParams.Fair(fairnessThreshold = 0.42)
        val json = mapper.writeValueAsString(original)
        assertEquals(original, mapper.readValue(json, NpcParams::class.java))
    }

    @Test
    fun `round-trip Selfish`() {
        val original: NpcParams = NpcParams.Selfish(minOffer = 5)
        assertEquals(original, mapper.readValue(mapper.writeValueAsString(original), NpcParams::class.java))
    }

    @Test
    fun `round-trip Random`() {
        val original: NpcParams = NpcParams.Random(acceptProbability = 0.75)
        assertEquals(original, mapper.readValue(mapper.writeValueAsString(original), NpcParams::class.java))
    }

    @Test
    fun `round-trip Vengeful`() {
        val original: NpcParams = NpcParams.Vengeful(baselineFraction = 0.4, punishStep = 2, fairnessThreshold = 0.25)
        assertEquals(original, mapper.readValue(mapper.writeValueAsString(original), NpcParams::class.java))
    }

    @Test
    fun `round-trip Adaptive`() {
        val original: NpcParams = NpcParams.Adaptive(baselineFraction = 0.4, targetRejectRate = 0.15, slope = 0.6)
        assertEquals(original, mapper.readValue(mapper.writeValueAsString(original), NpcParams::class.java))
    }
}
