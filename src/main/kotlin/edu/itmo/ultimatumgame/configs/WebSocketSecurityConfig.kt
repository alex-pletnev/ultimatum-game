package edu.itmo.ultimatumgame.configs

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.simp.SimpMessageType.CONNECT
import org.springframework.messaging.simp.SimpMessageType.DISCONNECT
import org.springframework.messaging.simp.SimpMessageType.HEARTBEAT
import org.springframework.messaging.simp.SimpMessageType.UNSUBSCRIBE
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager

@Configuration
@EnableWebSocketSecurity
class WebSocketSecurityConfig {

    /**
     * Подменяет default `CsrfChannelInterceptor` в `clientInboundChannel` на no-op (T-070).
     *
     * Spring Security 6 через `@EnableWebSocketSecurity` регистрирует
     * `WebSocketMessageBrokerSecurityConfiguration`, который добавляет свой
     * `CsrfChannelInterceptor` в цепочку. `.csrf { it.disable() }` из HTTP-фильтр-чейна
     * `SecurityConfiguration` на STOMP-канал НЕ распространяется — это отдельная
     * цепочка.
     *
     * `@stomp/stompjs` в браузере не может отправить CSRF-токен во фрейме (нет API),
     * dev-фронт на :5173 не проходит same-origin с backend'ом на :8080 — CONNECT
     * падал с `MissingCsrfTokenException` (CloseStatus 1002) до того, как
     * `JwtStompChannelInterceptor.preSend` видит Authorization-заголовок.
     *
     * `WebSocketMessageBrokerSecurityConfiguration` резолвит бин по имени
     * `csrfChannelInterceptor` через `getBeanOrNull(...)` — определив свой `@Bean`
     * с этим именем, мы подменяем default'ный на no-op интерцептор.
     *
     * Legacy `AbstractSecurityWebSocketMessageBrokerConfigurer` (с `sameOriginDisabled()`)
     * для этого не подходит — он конфликтует с `@EnableWebSocketSecurity` и ломает
     * bean-wiring (`No qualifying bean of type ChannelSecurityInterceptor`).
     *
     * Аутентификация STOMP по-прежнему на JWT (`JwtStompChannelInterceptor`).
     * Origin-check WS отдельно контролируется через `setAllowedOrigins` в
     * `WebSocketConfig` — для prod там надо сузить `*` до конкретных доменов.
     */
    @Bean(name = ["csrfChannelInterceptor"])
    fun noOpCsrfChannelInterceptor(): ChannelInterceptor = object : ChannelInterceptor {}

    @Bean
    fun messageAuthorizationManager(
        messages: MessageMatcherDelegatingAuthorizationManager.Builder
    ): AuthorizationManager<Message<*>> {
        // ---------- команды (SEND) ----------
        messages
            .simpDestMatchers("/app/session/*/offer.create").hasAnyRole("PLAYER", "ADMIN")
            .simpDestMatchers("/app/session/*/make.decision").hasAnyRole("PLAYER", "ADMIN")
            .simpDestMatchers("/app/session/*/start").hasRole("ADMIN")
            .simpDestMatchers("/app/session/*/close").hasRole("ADMIN")
            .simpDestMatchers("/app/session/*/open").hasRole("ADMIN")
            .simpDestMatchers("/app/session/*/round.start").hasRole("ADMIN")
            .simpDestMatchers("/app/session/*/round.abort").hasRole("ADMIN")

        // ---------- подписки (SUBSCRIBE) ----------
        messages
            .simpSubscribeDestMatchers("/topic/session/*/offerCreated")
            .hasAnyRole("PLAYER", "OBSERVER", "ADMIN")
        messages
            .simpSubscribeDestMatchers("/topic/session/*/decisionMade")
            .hasAnyRole("PLAYER", "OBSERVER", "ADMIN")
        messages
            .simpSubscribeDestMatchers("/topic/session/*/sessionStatus")
            .hasAnyRole("PLAYER", "OBSERVER", "ADMIN")
        messages
            .simpSubscribeDestMatchers("/topic/session/*/roundStatus")
            .hasAnyRole("PLAYER", "OBSERVER", "ADMIN")
        messages
            .simpSubscribeDestMatchers("/topic/session/*/scoreUpdated")
            .hasAnyRole("PLAYER", "OBSERVER", "ADMIN")
        messages
            .simpSubscribeDestMatchers("/topic/session/*/offersShuffled")
            .hasAnyRole("PLAYER", "OBSERVER", "ADMIN")
        messages
            .simpSubscribeDestMatchers("/topic/session/*/player/*/offer")
            .hasAnyRole("PLAYER", "ADMIN")

        // Персональная очередь ошибок STOMP-контроллеров (T-050): любой authenticated
        // клиент подписывается на /user/queue/errors — Spring роутит по principal.
        // Без этого matcher'а SUBSCRIBE попадает под anyMessage().denyAll() и клиент
        // не получает свои же ошибки.
        messages
            .simpSubscribeDestMatchers("/user/queue/errors")
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
