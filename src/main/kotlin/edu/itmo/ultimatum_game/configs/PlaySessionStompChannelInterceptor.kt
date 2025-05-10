package edu.itmo.ultimatum_game.configs

import edu.itmo.ultimatum_game.repositories.SessionRepository
import edu.itmo.ultimatum_game.services.SessionService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component

@Component
class PlaySessionStompChannelInterceptor(
    private val sessionService: SessionService,

) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        if (accessor.command != StompCommand.SEND && accessor.command != StompCommand.SUBSCRIBE) return message



    }
}