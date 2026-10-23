package edu.itmo.ultimatumgame

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import edu.itmo.ultimatumgame.controllers.ws.SessionAdminWsController
import edu.itmo.ultimatumgame.dto.requests.CreateOfferCmd
import edu.itmo.ultimatumgame.dto.requests.MakeDecisionCmd
import edu.itmo.ultimatumgame.services.PlayerGameplayService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ProductScenariosIntegrationTest {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var gameplay: PlayerGameplayService

    @Autowired private lateinit var hostCommands: SessionAdminWsController

    private fun register(role: String = "PLAYER"): String {
        val response = mvc.perform(
            post("/auth/quick-register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"product-${UUID.randomUUID().toString().take(8)}","role":"$role"}""")
        )
            .andReturn().response
        assertEquals(201, response.status)
        return mapper.readTree(response.contentAsString)["accessToken"].asText()
    }

    private fun send(path: String, token: String, body: String, status: Int = 201): JsonNode {
        val response = mvc.perform(
            post(path).header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andReturn().response
        assertEquals(status, response.status, response.contentAsString)
        return mapper.readTree(response.contentAsString)
    }

    private fun read(path: String, token: String): JsonNode {
        val response = mvc.perform(get(path).header("Authorization", "Bearer $token")).andReturn().response
        assertEquals(200, response.status, response.contentAsString)
        return mapper.readTree(response.contentAsString)
    }

    @Test
    fun `a player hosts and participates, strangers cannot manage or see it in their history`() {
        val owner = register()
        val outsider = register("ADMIN")
        val game =
            send(
                "/session",
                owner,
                """{
            "displayName":"Party", "hostPlays":true,
            "config":{"sessionType":"FREE_FOR_ALL","numPlayers":4,"numRounds":3,
            "roundSum":100,"timeoutMoveSec":60,"numTeams":0}}
        """
            )
        val id = game["id"].asText()
        assertEquals(1, game["members"].size())
        assertEquals(game["admin"]["id"], game["members"][0]["id"])
        assertTrue(read("/session/mine", owner)["content"].any { it["id"].asText() == id })
        assertTrue(read("/session/mine", outsider)["content"].none { it["id"].asText() == id })
        send(
            "/session/$id/npcs",
            outsider,
            """{"count":1,"strategy":"FAIR","params":{"type":"FAIR","fairnessThreshold":0.5}}""",
            403,
        )
        send("/session/$id/rematch", outsider, "{}", 403)
        val rematch = send("/session/$id/rematch", owner, "{}")
        assertTrue(rematch["id"].asText() != id)
        assertEquals("CREATED", rematch["state"].asText())
        assertEquals(game["config"], rematch["config"])
        assertEquals(1, rematch["members"].size())
    }

    @Test
    fun `solo dictator resolves both offers without asking for decisions`() {
        val owner = register()
        val game = send(
            "/session/solo",
            owner,
            """{"strategy":"FAIR","sessionType":"DICTATOR","numRounds":3}"""
        )
        val id = UUID.fromString(game["id"].asText())
        val player = UUID.fromString(read("/user", owner)["id"].asText())
        assertEquals("RUNNING", game["state"].asText())
        assertEquals(2, game["members"].size())
        gameplay.sendOffer(id, player, CreateOfferCmd(amount = 10))
        val round = read("/session/$id/current-round", owner)
        assertEquals("ALL_DECISIONS_RECEIVED", round["roundPhase"].asText())
        assertEquals(2, round["decisions"].size())
        assertTrue(round["decisions"].all { it["decision"].asBoolean() })
        assertEquals(0, round["myPendingActions"].size())
    }

    @Test
    fun `outsiders cannot send offers or decide another players offer`() {
        val owner = register()
        val outsider = register()
        val game = send("/session/solo", owner, "{}")
        val id = UUID.fromString(game["id"].asText())
        val player = UUID.fromString(read("/user", owner)["id"].asText())
        val stranger = UUID.fromString(read("/user", outsider)["id"].asText())
        assertFailsWith<AccessDeniedException> { gameplay.sendOffer(id, stranger, CreateOfferCmd(50)) }
        gameplay.sendOffer(id, player, CreateOfferCmd(50))
        val round = read("/session/$id/current-round", owner)
        val offerId = round["myPendingActions"][0]["offerId"].asText()
        assertFailsWith<AccessDeniedException> {
            gameplay.makeDecision(id, stranger, MakeDecisionCmd(offerId, true))
        }
        gameplay.makeDecision(id, player, MakeDecisionCmd(offerId, true))
        assertEquals("ALL_DECISIONS_RECEIVED", read("/session/$id/current-round", owner)["roundPhase"].asText())
    }

    @Test
    fun `solo rejects unsupported teams and invalid round counts`() {
        val token = register()
        send("/session/solo", token, """{"sessionType":"TEAM_BATTLE"}""", 400)
        send("/session/solo", token, """{"numRounds":0}""", 400)
        send("/session/solo", token, """{"numRounds":11}""", 400)
    }

    @Test
    fun `foreign admins cannot invoke any hosting command through the secured controller`() {
        val owner = register()
        val outsider = register("ADMIN")
        val game = send("/session/solo", owner, "{}")
        val id = game["id"].asText()
        val before = read("/session/$id/current-round", owner)
        val principal = TestingAuthenticationToken(read("/user", outsider)["id"].asText(), "", "ROLE_ADMIN")
        SecurityContextHolder.getContext().authentication = principal
        try {
            assertFailsWith<AccessDeniedException> { hostCommands.startSession(id, principal) }
            assertFailsWith<AccessDeniedException> { hostCommands.startNextRound(id, principal) }
            assertFailsWith<AccessDeniedException> { hostCommands.abortCurrentRound(id, principal) }
            assertFailsWith<AccessDeniedException> { hostCommands.openSession(id, principal) }
            assertFailsWith<AccessDeniedException> { hostCommands.closeSession(id, principal) }
        } finally {
            SecurityContextHolder.clearContext()
        }
        val after = read("/session/$id/current-round", owner)
        assertEquals(before["id"], after["id"])
        assertEquals(before["roundPhase"], after["roundPhase"])
        assertEquals(before["offers"], after["offers"])
        assertEquals("RUNNING", read("/session/$id", owner)["state"].asText())
    }
}
