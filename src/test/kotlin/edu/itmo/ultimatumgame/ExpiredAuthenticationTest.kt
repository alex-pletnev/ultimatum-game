package edu.itmo.ultimatumgame

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Date
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ExpiredAuthenticationTest {
    @Autowired private lateinit var mvc: MockMvc

    @Value("\${token.signing.key}")
    private lateinit var signingKey: String

    @Test
    fun `expired access is unauthorized so clients can refresh instead of failing with server error`() {
        val expired = Jwts.builder().setSubject(UUID.randomUUID().toString())
            .setExpiration(Date(1)).claim("type", "ACCESS")
            .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(signingKey)), SignatureAlgorithm.HS256)
            .compact()
        mvc.perform(get("/user").header("Authorization", "Bearer $expired"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().encoding("UTF-8"))
    }

    @Test
    fun `malformed bearer is unauthorized without a servlet exception`() {
        mvc.perform(get("/user").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().encoding("UTF-8"))
    }
}
