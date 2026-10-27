package edu.itmo.ultimatumgame

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import edu.itmo.ultimatumgame.dto.requests.CreateOfferCmd
import edu.itmo.ultimatumgame.services.AdminGameplayService
import edu.itmo.ultimatumgame.services.PlayerGameplayService
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class GroupPlayIntegrationTest {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var gameplay: AdminGameplayService

    @Autowired private lateinit var players: PlayerGameplayService

    @ParameterizedTest
    @ValueSource(ints = [100, 101])
    fun `host can enable fair autopilot without losing membership or duplicating moves`(roundSum: Int) {
        val host = register("Host")
        val guest = register("Guest")
        val game = create(host, false, roundSum)
        val id = game["id"].asText()
        val guestId = read("/user", guest)["id"].asText()
        send("/session/$id/join", guest)
        gameplay.startSession(UUID.fromString(id))
        send("/session/$id/autopilot/$guestId?enabled=true", guest, expected = 403)
        send("/session/$id/autopilot/$guestId?enabled=true", host)
        send("/session/$id/autopilot/$guestId?enabled=true", host)
        val round = read("/session/$id/current-round", host)
        assertEquals(1, round["offers"].size())
        val fairShare = (roundSum + 1) / 2
        assertEquals(fairShare, round["offers"][0]["offerValue"].asInt())
        val hostId = game["members"][0]["id"].asText()
        players.sendOffer(UUID.fromString(id), UUID.fromString(hostId), CreateOfferCmd(fairShare))
        val decided = read("/session/$id/current-round", host)
        assertEquals(1, decided["decisions"].size())
        assertTrue(decided["decisions"][0]["decision"].asBoolean())
        val csv = mvc.perform(get("/statistics/$id/csv")).andReturn().response.contentAsString
        assertTrue(csv.contains("offerAutomatic"))
        assertTrue(csv.contains("decisionAutomatic"))
        send("/session/$id/autopilot/$guestId?enabled=false", host)
        assertEquals(2, read("/session/$id/with-teams-and-members", host)["members"].size())
        assertEquals(0, read("/session/$id/with-teams-and-members", host)["autopilotPlayerIds"].size())
    }

    @Test
    fun `anonymous rounds and csv hide real identities but keep stable participant aliases`() {
        val host = register("Named host")
        val guest = register("Named guest")
        val game = create(host, true)
        val id = game["id"].asText()
        send("/session/$id/join", guest)
        gameplay.startSession(UUID.fromString(id))
        val hostId = game["members"][0]["id"].asText()
        val guestId = read("/user", guest)["id"].asText()
        players.sendOffer(UUID.fromString(id), UUID.fromString(hostId), CreateOfferCmd(40))
        players.sendOffer(UUID.fromString(id), UUID.fromString(guestId), CreateOfferCmd(50))
        val round = read("/session/$id/current-round", guest)
        val offers = round["offers"].toString()
        assertFalse(offers.contains("Named"))
        assertFalse(offers.contains(hostId))
        assertFalse(offers.contains(guestId))
        val csv = mvc.perform(get("/statistics/$id/csv")).andReturn().response.contentAsString
        assertFalse(csv.contains("Named"))
        assertFalse(csv.contains(hostId))
        assertFalse(csv.contains(guestId))
        assertTrue(csv.contains(round["offers"][0]["proposer"]["id"].asText()))
    }

    private fun register(name: String): String = send(
        "/auth/quick-register",
        body = mapper.writeValueAsString(mapOf("nickname" to name)),
        expected = 201
    )["accessToken"].asText()

    private fun create(token: String, anonymous: Boolean, roundSum: Int = 100): JsonNode = send(
        "/session",
        token,
        """{"displayName":"Group test","hostPlays":true,"config":{"numPlayers":2,"numRounds":2,
            "roundSum":$roundSum,"timeoutMoveSec":60,"anonymous":$anonymous}}""",
        201
    )

    private fun read(path: String, token: String): JsonNode {
        val response = mvc.perform(get(path).header("Authorization", "Bearer $token")).andReturn().response
        assertEquals(200, response.status, response.contentAsString)
        return mapper.readTree(response.contentAsString)
    }

    private fun send(path: String, token: String? = null, body: String = "{}", expected: Int = 200): JsonNode {
        val request = post(path).contentType(MediaType.APPLICATION_JSON).content(body)
        if (token != null) request.header("Authorization", "Bearer $token")
        val response = mvc.perform(request).andReturn().response
        assertEquals(expected, response.status, response.contentAsString)
        return mapper.readTree(response.contentAsString)
    }
}
