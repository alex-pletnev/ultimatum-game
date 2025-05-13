package edu.itmo.ultimatum_game.configs

import edu.itmo.ultimatum_game.exceptions.SessionStompRejectedException
import edu.itmo.ultimatum_game.services.SessionService
import edu.itmo.ultimatum_game.util.logger
import edu.itmo.ultimatum_game.util.toUuidOrThrow
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

    private val logger = logger()

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        // Получаем STOMP-аксессор
        val accessor = MessageHeaderAccessor
            .getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message.also {
                logger.debug("Non-STOMP message, пропускаем")
            }

        val command = accessor.command
        // Интересуют только SEND и SUBSCRIBE
        if (command != StompCommand.SEND && command != StompCommand.SUBSCRIBE) {
            logger.trace("Игнорируем STOMP команду {}", command)
            return message
        }

        // Логируем попытку
        val dest = accessor.destination
        logger.info("Handling STOMP $command for destination=$dest")

        // Проверяем аутентификацию
        val principal = accessor.user
            ?: run {
                logger.warn("Прерываем: нет Principal для $command $dest")
                throw SessionStompRejectedException("Неавторизованный доступ к $dest")
            }
        val userId = try {
            principal.name.toUuidOrThrow()
        } catch (e: Exception) {
            logger.error("Не удалось распарсить userId='${principal.name}'", e)
            throw SessionStompRejectedException("Неверный формат userId")
        }
        logger.debug("Пользователь {} выполняет {} на {}", userId, command, dest)

        // Извлекаем sessionId из пути
        val sessionId = try {
            if (command == StompCommand.SEND) {
                dest
                    ?.substringAfter("/app/session/")
                    ?.substringBefore("/")
                    .toUuidOrThrow()
            } else {
                dest
                    ?.substringAfter("/topic/session/")
                    ?.substringBefore("/")
                    .toUuidOrThrow()
            }
        } catch (e: Exception) {
            logger.error("Не удалось извлечь sessionId из '$dest'", e)
            throw SessionStompRejectedException("Неверный путь '$dest'")
        }
        logger.debug("Извлечён sessionId={}", sessionId)

        val userIdFromPath =
            if (dest?.endsWith("/offer") == true) {
                try {
                    dest
                        .substringAfter("/player/")
                        .substringBefore("/")
                        .toUuidOrThrow()
                } catch (e: Exception) {
                    logger.error("Не удалось извлечь userId из '$dest'", e)
                    throw SessionStompRejectedException("Неверный путь '$dest'")
                }
            } else {
                null
            }

        // Проверяем права
        return when (command) {
            StompCommand.SEND -> {
                if (sessionService.isUserAreSessionAdmin(userId, sessionId)) {
                    logger.info("SEND разрешён: пользователь $userId — админ сессии $sessionId")
                    message
                } else {
                    logger.warn("SEND отклонён: пользователь $userId не админ сессии $sessionId")
                    throw SessionStompRejectedException("Вы не являетесь админом сессии $sessionId")
                }
            }

            StompCommand.SUBSCRIBE -> {
                val allowed = sessionService.isUserAreSessionAdmin(userId, sessionId)
                        || sessionService.isUserAreSessionMember(userId, sessionId)
                        || sessionService.isUserAreSessionObserver(userId, sessionId)
                if (allowed) {

                    if (userIdFromPath != null && userIdFromPath != userId) {
                        throw SessionStompRejectedException("Слушать топик $dest может только пользователь $userIdFromPath")
                    }
                    logger.info("SUBSCRIBE разрешён: пользователь $userId может слушать сессию $sessionId")
                    message
                } else {
                    logger.warn("SUBSCRIBE отклонён: пользователь $userId не участник/зритель/админ сессии $sessionId")
                    throw SessionStompRejectedException(
                        "Вы не админ/игрок/зритель сессии $sessionId"
                    )
                }
            }

            else -> {
                // сюда не попадём
                message
            }
        }
    }
}
