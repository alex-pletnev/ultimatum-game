package edu.itmo.ultimatumgame

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import edu.itmo.ultimatumgame.model.NpcStrategy
import edu.itmo.ultimatumgame.repositories.NpcProfileRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class RandomNpcsIntegrationTest {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var profiles: NpcProfileRepository

    @Autowired private lateinit var broker: SimpleBrokerMessageHandler

    @Test
    fun `broker preserves publication order so the final lobby snapshot arrives last`() {
        assertTrue(broker.isPreservePublishOrder)
    }

    @Test
    fun `host fills all remaining seats with mixed bots and repeated fill adds nothing`() {
        val owner = register()
        val game = createGame(owner, 120)
        val filled = send("/session/${game["id"].asText()}/npcs/random", owner)
        assertEquals(120, filled["members"].size())
        assertEquals("CREATED", filled["state"].asText())
        assertFalse(filled["openToConnect"].asBoolean())
        assertTrue(filled["members"].any { it["id"] == game["members"][0]["id"] })
        val bots = filled["members"].filter { it["role"].asText() == "NPC" }
        assertEquals(119, bots.size)
        val strategies = bots.map { profiles.findByUserId(UUID.fromString(it["id"].asText()))!!.strategy }
        assertEquals(NpcStrategy.entries.toSet(), strategies.toSet())
        val repeated = send("/session/${game["id"].asText()}/npcs/random", owner)
        assertEquals(filled["members"].map { it["id"] }.toSet(), repeated["members"].map { it["id"] }.toSet())
    }

    @ParameterizedTest
    @ValueSource(strings = ["PLAYER", "ADMIN"])
    fun `only the owner can fill the lobby`(role: String) {
        val owner = register()
        val game = createGame(owner, 4)
        send("/session/${game["id"].asText()}/npcs/random", register(role), status = 403)
        val filled = send("/session/${game["id"].asText()}/npcs/random", owner)
        assertEquals(4, filled["members"].size())
    }

    @ParameterizedTest
    @ValueSource(ints = [2, 3])
    fun `team fill respects per-team capacity for odd player limits`(teams: Int) {
        val owner = register()
        val game = createGame(owner, 7, teams)
        val filled = send("/session/${game["id"].asText()}/npcs/random", owner)
        assertEquals(if (teams == 2) 6 else 7, filled["members"].size())
        assertTrue(filled["teams"].all { it["members"].size() <= 3 })
        assertEquals(filled["members"].size(), filled["teams"].sumOf { it["members"].size() })
        val repeated = send("/session/${game["id"].asText()}/npcs/random", owner)
        assertEquals(filled["members"].size(), repeated["members"].size())
    }

    @Test
    fun `running sessions reject random fill even when already full`() {
        val owner = register()
        val game = send("/session/solo", owner, "{}", 201)
        send("/session/${game["id"].asText()}/npcs/random", owner, status = 409)
    }

    @Test
    fun `concurrent fills do not overfill or create duplicate bots`() {
        val owner = register()
        val game = createGame(owner, 8)
        val calls = List(2) {
            CompletableFuture.supplyAsync { send("/session/${game["id"].asText()}/npcs/random", owner) }
        }
        val results = calls.map { it.get(10, TimeUnit.SECONDS) }
        assertEquals(8, results[0]["members"].size())
        assertEquals(results[0]["members"].map { it["id"] }.toSet(), results[1]["members"].map { it["id"] }.toSet())
    }

    private fun register(role: String = "PLAYER"): String = send(
        "/auth/quick-register",
        body = """{"nickname":"random-${UUID.randomUUID().toString().take(8)}","role":"$role"}""",
        status = 201,
    )["accessToken"].asText()

    private fun createGame(owner: String, players: Int, teams: Int = 0): JsonNode = send(
        "/session",
        owner,
        """{"displayName":"Random bots","hostPlays":true,
            "config":{"sessionType":"${if (teams == 0) "FREE_FOR_ALL" else "TEAM_BATTLE"}",
            "numPlayers":$players,"numTeams":$teams,"numRounds":1,"roundSum":100,"timeoutMoveSec":60}}
        """,
        201,
    )

    private fun send(path: String, token: String = "", body: String = "", status: Int = 200): JsonNode {
        val response = mvc.perform(
            post(path).apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andReturn().response
        assertEquals(status, response.status, response.contentAsString)
        return mapper.readTree(response.contentAsString)
    }
}
