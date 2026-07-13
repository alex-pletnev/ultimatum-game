package edu.itmo.ultimatumgame.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SecurityServiceTest {

    private val service = SecurityService()

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `getCurrentUserId — возвращает UUID из authentication_name`() {
        val expected = UUID.randomUUID()
        val auth = mockk<Authentication> { every { name } returns expected.toString() }
        val ctx = mockk<SecurityContext> { every { authentication } returns auth }
        SecurityContextHolder.setContext(ctx)

        assertEquals(expected, service.getCurrentUserId())
    }

    @Test
    fun `getCurrentUserId — бросает IllegalArgumentException при невалидном UUID в контексте`() {
        val auth = mockk<Authentication> { every { name } returns "not-a-uuid" }
        val ctx = mockk<SecurityContext> { every { authentication } returns auth }
        SecurityContextHolder.setContext(ctx)

        assertThrows<IllegalArgumentException> { service.getCurrentUserId() }
    }
}
