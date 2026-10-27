package edu.itmo.ultimatumgame.configs

import edu.itmo.ultimatumgame.TestFixtures.user
import edu.itmo.ultimatumgame.services.JwtService
import edu.itmo.ultimatumgame.services.UserService
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.util.UUID
import kotlin.test.assertNotNull

class PrivateOfferSubscriptionTest {
    @Test
    fun `players cannot subscribe to another participant private offer`() {
        val interceptor = JwtStompChannelInterceptor(mockk<JwtService>(), mockk<UserService>())
        val player = user()
        val headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        headers.user = UsernamePasswordAuthenticationToken(player, null, player.authorities)
        headers.destination = "/topic/session/${UUID.randomUUID()}/player/${UUID.randomUUID()}/offer"
        val channel = mockk<MessageChannel>()
        assertThrows<AccessDeniedException> {
            interceptor.preSend(MessageBuilder.createMessage(ByteArray(0), headers.messageHeaders), channel)
        }
        val ownHeaders = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        ownHeaders.user = UsernamePasswordAuthenticationToken(player, null, player.authorities)
        ownHeaders.destination = "/topic/session/${UUID.randomUUID()}/player/${player.id}/offer"
        assertNotNull(
            interceptor.preSend(MessageBuilder.createMessage(ByteArray(0), ownHeaders.messageHeaders), channel)
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["/topic/**", "/topic/session/*/player/*/offer", "/topic/session/{game}/**"])
    fun `wildcard subscriptions cannot bypass recipient checks`(destination: String) {
        val interceptor = JwtStompChannelInterceptor(mockk<JwtService>(), mockk<UserService>())
        val player = user()
        val headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        headers.user = UsernamePasswordAuthenticationToken(player, null, player.authorities)
        headers.destination = destination
        assertThrows<AccessDeniedException> {
            interceptor.preSend(
                MessageBuilder.createMessage(ByteArray(0), headers.messageHeaders),
                mockk<MessageChannel>()
            )
        }
    }
}
