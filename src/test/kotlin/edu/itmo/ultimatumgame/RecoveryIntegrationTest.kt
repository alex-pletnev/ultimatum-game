package edu.itmo.ultimatumgame

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class RecoveryIntegrationTest {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var mapper: ObjectMapper

    @Test
    fun `secret code restores same profile and public user id cannot log in`() {
        val registration = postJson("/auth/quick-register", """{"nickname":"Recovery test"}""", 201)
        assertTrue(registration.hasNonNull("recoveryCode"), "Registration must issue a recovery code")
        val original = profile(registration["accessToken"].asText())
        val recovered = postJson(
            "/auth/quick-login",
            mapper.writeValueAsString(mapOf("recoveryCode" to registration["recoveryCode"].asText())),
            200
        )
        assertEquals(original["id"], profile(recovered["accessToken"].asText())["id"])
        postJson("/auth/quick-login", """{"id":"${original["id"].asText()}"}""", 400)
        postJson("/auth/quick-login", """{"recoveryCode":"not-a-valid-code"}""", 401)
    }

    private fun profile(token: String): JsonNode = mapper.readTree(
        mvc.perform(get("/user").header("Authorization", "Bearer $token")).andReturn().response.contentAsString
    )

    private fun postJson(path: String, body: String, expected: Int): JsonNode {
        val response = mvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content(body)
        ).andReturn().response
        assertEquals(expected, response.status, response.contentAsString)
        return mapper.readTree(response.contentAsString)
    }
}
