package edu.itmo.ultimatum_game.configs

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.simp.SimpMessageType.*
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager


@Configuration
@EnableWebSocketSecurity
class WebSocketSecurityConfig {


    @Bean
    fun messageAuthorizationManager(
        messages: MessageMatcherDelegatingAuthorizationManager.Builder
    ): AuthorizationManager<Message<*>> {
        // ---------- команды (SEND) ----------
        messages
            .simpDestMatchers("/app/session/*/offer.create").hasRole("PLAYER")
            .simpDestMatchers("/app/offer/*/decision").hasRole("PLAYER")
            .simpDestMatchers("/app/session/*/round.finish").hasRole("ADMIN")
            .simpDestMatchers("/app/session/*/round.start").hasRole("ADMIN")

        // ---------- подписки (SUBSCRIBE) ----------
        messages
            .simpSubscribeDestMatchers("/topic/session/*/offerCreated")
            .hasAnyRole("PLAYER", "OBSERVER", "ADMIN")
        messages
            .simpSubscribeDestMatchers("/topic/session/*/decisionMade")
            .hasAnyRole("PLAYER", "OBSERVER", "ADMIN")
        messages
            .simpSubscribeDestMatchers("/topic/session/*/roundStatus")
            .hasAnyRole("PLAYER", "OBSERVER", "ADMIN")

        messages
            .simpTypeMatchers(CONNECT, HEARTBEAT, UNSUBSCRIBE, DISCONNECT)
            .permitAll()

        // Блокируем всё остальное
        messages.anyMessage().denyAll()

        // Возвращаем готовый AuthorizationManager
        return messages.build()
    }

}