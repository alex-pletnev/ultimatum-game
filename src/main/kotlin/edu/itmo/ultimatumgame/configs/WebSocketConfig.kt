package edu.itmo.ultimatumgame.configs

import edu.itmo.ultimatumgame.util.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.server.HandshakeInterceptor

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val jwtStompChannelInterceptor: JwtStompChannelInterceptor,
    @Value("\${app.cors.origins:http://localhost:[*]}") private val corsOriginsCsv: String,
) :
    WebSocketMessageBrokerConfigurer {

    private val logger = logger()

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    // T-090: Spring API принимает только vararg — spread над короткой List<String>
    // (обычно 1-3 origin'а) неизбежен и безопасен.
    @Suppress("SpreadOperator")
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        val originPatterns = corsOriginsCsv.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toTypedArray()
        registry.addEndpoint("/ws")
            .addInterceptors(
                object : HandshakeInterceptor {
                    override fun beforeHandshake(
                        request: ServerHttpRequest,
                        response: ServerHttpResponse,
                        wsHandler: WebSocketHandler,
                        attributes: MutableMap<String, Any>
                    ): Boolean {
                        logger.info("Handshake principal = ${request.principal}")
                        return true
                    }

                    @Suppress("EmptyFunctionBlock") // intentional no-op after WebSocket handshake
                    override fun afterHandshake(
                        request: ServerHttpRequest,
                        response: ServerHttpResponse,
                        wsHandler: WebSocketHandler,
                        exception: Exception?
                    ) {}
                }
            )
            .setAllowedOriginPatterns(*originPatterns)
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(jwtStompChannelInterceptor)
    }
}
