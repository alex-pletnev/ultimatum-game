package edu.itmo.ultimatumgame.exceptions

import edu.itmo.ultimatumgame.dto.responses.ApiErrorResponse
import edu.itmo.ultimatumgame.util.logger
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.SignatureException
import org.springframework.http.HttpStatus
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.web.bind.annotation.ControllerAdvice
import java.util.Date

/**
 * Единый обработчик ошибок STOMP-контроллеров (T-050).
 *
 * До этого исключения из `@MessageMapping`-методов молча логировались Spring'ом и уходили
 * дефолтным ERROR-фреймом без стабильного payload'а — фронт не мог показать пользователю
 * причину отказа (например «amount > roundSum»). Теперь любая ошибка из STOMP-контроллеров
 * пробрасывается как `ApiErrorResponse` в персональную очередь `/user/queue/errors`,
 * симметрично REST-ответам из [GlobalExceptionsHandler].
 *
 * Клиент подписывается на `/user/queue/errors` — Spring роутит по principal.
 * Если principal отсутствует (не должно случаться после JwtStompChannelInterceptor)
 * — ошибка только логируется.
 */
@ControllerAdvice
class WebSocketExceptionAdvice {

    private val log = logger()

    @MessageExceptionHandler(Exception::class)
    @SendToUser("/queue/errors", broadcast = false)
    fun handleException(ex: Exception): ApiErrorResponse {
        val status = statusFor(ex)
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("Необработанное исключение в STOMP-контроллере", ex)
        } else {
            log.warn(
                "STOMP-контроллер отказал: status={}, ex={}, msg={}",
                status.value(),
                ex::class.simpleName,
                ex.message,
            )
        }
        return ApiErrorResponse(
            timestamp = Date(),
            status = status.value(),
            error = status.reasonPhrase,
            message = messageFor(ex, status),
            path = "stomp",
        )
    }

    @Suppress("ComplexMethod", "ReturnCount")
    private fun statusFor(ex: Exception): HttpStatus = when (ex) {
        is InvalidJwtException,
        is ExpiredJwtException,
        is SignatureException,
        is MalformedJwtException -> HttpStatus.UNAUTHORIZED

        is UserRoleNotAllowedException -> HttpStatus.FORBIDDEN

        is IdNotFoundException -> HttpStatus.NOT_FOUND

        is DuplicateIdException,
        is SessionJoinRejectedException,
        is IllegalStateException -> HttpStatus.CONFLICT

        is InvalidUuidFormatException,
        is IllegalArgumentException,
        is InvalidOfferException -> HttpStatus.BAD_REQUEST

        else -> HttpStatus.INTERNAL_SERVER_ERROR
    }

    private fun messageFor(ex: Exception, status: HttpStatus): String {
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) return "Внутренняя ошибка сервера"
        return ex.message ?: status.reasonPhrase
    }
}
