package edu.itmo.ultimatum_game.configs

import edu.itmo.ultimatum_game.exceptions.InvalidJwtException
import edu.itmo.ultimatum_game.services.JwtService
import edu.itmo.ultimatum_game.services.UserService
import edu.itmo.ultimatum_game.util.logger
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.*

@Component
class JwtStompChannelInterceptor(
    private val jwtService: JwtService,
    private val userService: UserService,
) : ChannelInterceptor {

    private val logger = logger()

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        logger.info(
            "в JwtStompChannelInterceptor.preSend пришла команда: {} от {} / {}",
            accessor.command,
            SecurityContextHolder.getContext().authentication,
            SecurityContextHolder.getContext().authentication !is AnonymousAuthenticationToken
        )
        if (accessor.command == StompCommand.SUBSCRIBE) {
            logger.info(
                ">>> SUBSCRIBE interceptor: destination=${accessor.destination}, " +
                        "user=${accessor.user}, " +
                        "SecurityContext=${SecurityContextHolder.getContext().authentication}"
            )
        }

        //проверяем что пришел CONNECT
        if (accessor.command != StompCommand.CONNECT) return message

        val authHeader = accessor.getFirstNativeHeader(HEADER_AUTHORIZATION)
        if (authHeader.isNullOrBlank() || !authHeader.startsWith(BEARER_PREFIX)) {
            logger.warn("CONNECT без Authorization-заголовка — соединение будет закрыто")
            throw AuthenticationCredentialsNotFoundException("Отсутствует JWT")
        }

        val jwt = authHeader.removePrefix(BEARER_PREFIX)
        val username = jwtService.extractUsername(jwt)
        if (username.isNotBlank() && (SecurityContextHolder.getContext().authentication == null || SecurityContextHolder.getContext().authentication is AnonymousAuthenticationToken)) {
            logger.info("Попытка аутентификации пользователя с id=$username по токену")
            val userDetail = userService.getUserDetailService().invoke(UUID.fromString(username))

            if (jwtService.isTokenValid(jwt, userDetail)) {
                logger.info("Токен валиден для пользователя id=$username (${userDetail.role}) ")
                val context = SecurityContextHolder.createEmptyContext()

                val authorities = userDetail.authorities
                    .map { "ROLE_${it}" }
                    .map { SimpleGrantedAuthority(it) }

                val authToken = UsernamePasswordAuthenticationToken(
                    userDetail,
                    null,
                    authorities
                )
                accessor.user = authToken
                context.authentication = authToken
                SecurityContextHolder.setContext(context)
                logger.info("STOMP CONNECT аутентифицирован: id=$username, role(s)=$authorities")
            } else {
                logger.warn("Невалидный токен для пользователя id=$username")
                throw InvalidJwtException("Невалидный jwt, попробуйте с повторить логин")
            }

        } else {
            if (username.isBlank()) {
                logger.warn("Не удалось извлечь username из токена")
                throw InvalidJwtException("Невалидный jwt (не удалось извлечь пользователя), попробуйте с повторить логин")
            }
            if (SecurityContextHolder.getContext().authentication == null || SecurityContextHolder.getContext().authentication is AnonymousAuthenticationToken)
                logger.debug("Пользователь уже аутентифицирован, пропускаем фильтр")
        }
        return message
    }
}