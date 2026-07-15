package edu.itmo.ultimatumgame.exceptions

import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.SignatureException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebSocketExceptionAdviceTest {

    private val advice = WebSocketExceptionAdvice()

    @Test
    fun `IllegalArgumentException → 400 + сообщение исключения`() {
        val resp = advice.handleException(IllegalArgumentException("amount > roundSum"))
        assertEquals(400, resp.status)
        assertEquals("amount > roundSum", resp.message)
    }

    @Test
    fun `IdNotFoundException → 404`() {
        val resp = advice.handleException(IdNotFoundException("no such offer"))
        assertEquals(404, resp.status)
    }

    @Test
    fun `DuplicateIdException → 409`() {
        val resp = advice.handleException(DuplicateIdException("dup"))
        assertEquals(409, resp.status)
    }

    @Test
    fun `IllegalStateException → 409 (нарушение фазы)`() {
        val resp = advice.handleException(IllegalStateException("wrong phase"))
        assertEquals(409, resp.status)
    }

    @Test
    fun `SignatureException JWT → 401`() {
        val resp = advice.handleException(SignatureException("bad sig"))
        assertEquals(401, resp.status)
    }

    @Test
    fun `MalformedJwtException → 401`() {
        val resp = advice.handleException(MalformedJwtException("bad structure"))
        assertEquals(401, resp.status)
    }

    @Test
    fun `неизвестное исключение → 500 без деталей (нет утечки stack-trace)`() {
        val resp = advice.handleException(RuntimeException("SecretInternalMessage: token=abc"))
        assertEquals(500, resp.status)
        assertEquals("Внутренняя ошибка сервера", resp.message)
        assertFalse(resp.message.contains("abc"))
    }
}
