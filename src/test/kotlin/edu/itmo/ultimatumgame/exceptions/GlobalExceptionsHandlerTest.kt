package edu.itmo.ultimatumgame.exceptions

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GlobalExceptionsHandlerTest {

    private val handler = GlobalExceptionsHandler()
    private val request = mockk<HttpServletRequest>().apply {
        every { requestURI } returns "/api/v1/session"
    }

    // T-050: fallback handler не должен утекать stack-trace в message.
    @Test
    fun `handleAllExceptions — message без stack-trace, без деталей исключения`() {
        val ex = RuntimeException("SecretInternalMessage: db-password=hunter2")

        val resp = handler.handleAllExceptions(ex, request)

        assertEquals(500, resp.status)
        assertEquals("Внутренняя ошибка сервера", resp.message)
        assertFalse(resp.message.contains("hunter2"), "message не должен содержать секрет из исключения")
        assertFalse(resp.message.contains("at "), "message не должен содержать элементы stack-trace")
    }
}
