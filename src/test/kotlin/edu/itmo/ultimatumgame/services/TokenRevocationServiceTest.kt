package edu.itmo.ultimatumgame.services

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenRevocationServiceTest {

    private val service = TokenRevocationService()

    @Test
    fun `never revoked jti — isRevoked returns false`() {
        assertFalse(service.isRevoked(UUID.randomUUID()))
    }

    @Test
    fun `revoke then isRevoked returns true`() {
        val jti = UUID.randomUUID()
        service.revoke(jti)
        assertTrue(service.isRevoked(jti))
    }

    @Test
    fun `revoking one jti does not affect other jti`() {
        val revoked = UUID.randomUUID()
        val other = UUID.randomUUID()
        service.revoke(revoked)
        assertTrue(service.isRevoked(revoked))
        assertFalse(service.isRevoked(other))
    }

    @Test
    fun `revoke is idempotent`() {
        val jti = UUID.randomUUID()
        service.revoke(jti)
        service.revoke(jti)
        assertTrue(service.isRevoked(jti))
    }
}
