package edu.itmo.ultimatum_game.exceptions

import edu.itmo.ultimatum_game.dto.responses.ApiErrorResponse
import io.jsonwebtoken.ExpiredJwtException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.*

@RestControllerAdvice
class GlobalExceptionsHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ApiErrorResponse =
        ApiErrorResponse(
            timestamp = Date(),
            status = 400,
            error = "Bad Request",
            message = ex.bindingResult
                .fieldErrors
                .joinToString("; ") { "${it.field}: ${it.defaultMessage}" },
            path = request.requestURI
        )


    @ExceptionHandler(AccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ApiErrorResponse =
        ApiErrorResponse(
            timestamp = Date(),
            status = HttpStatus.FORBIDDEN.value(),
            error = "Forbidden",
            message = ex.message ?: "Доступ запрещён",
            path = request.requestURI
        )


    @ExceptionHandler(UserRoleNotAllowedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleUserRoleNotAllowed(
        ex: UserRoleNotAllowedException,
        request: HttpServletRequest
    ): ApiErrorResponse =
        ApiErrorResponse(
            timestamp = Date(),
            status = HttpStatus.FORBIDDEN.value(),
            error = "Forbidden",
            message = ex.message ?: "Роль пользователя не разрешена",
            path = request.requestURI
        )


    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleAllExceptions(
        ex: Exception,
        request: HttpServletRequest
    ): ApiErrorResponse =
        ApiErrorResponse(
            timestamp = Date(),
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = (ex.message + "; st: -> " + ex.stackTraceToString()),
            path = request.requestURI
        )

    @ExceptionHandler(DuplicateIdException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleDuplicateId(
        ex: DuplicateIdException,
        request: HttpServletRequest
    ): ApiErrorResponse =
        ApiErrorResponse(
            timestamp = Date(),
            status = HttpStatus.CONFLICT.value(),
            error = "Conflict",
            message = ex.message ?: "Дубликат идентификатора",
            path = request.requestURI
        )

    @ExceptionHandler(IdNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleIdNotFound(
        ex: IdNotFoundException,
        request: HttpServletRequest
    ): ApiErrorResponse =
        ApiErrorResponse(
            timestamp = Date(),
            status = HttpStatus.NOT_FOUND.value(),
            error = "Not Found",
            message = ex.message ?: "Идентификатор не найден",
            path = request.requestURI
        )

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(
        ex: IllegalArgumentException, request: HttpServletRequest
    ): ApiErrorResponse = ApiErrorResponse(
        timestamp = Date(),
        status = HttpStatus.BAD_REQUEST.value(),
        error = "Bad Request",
        message = ex.message ?: "Недопустимый аргумент",
        path = request.requestURI
    )

    @ExceptionHandler(InvalidUuidFormatException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidUuidFormat(
        ex: InvalidUuidFormatException, request: HttpServletRequest
    ): ApiErrorResponse = ApiErrorResponse(
        timestamp = Date(),
        status = HttpStatus.BAD_REQUEST.value(),
        error = "Bad Request",
        message = ex.message ?: "Неверный формат UUID",
        path = request.requestURI
    )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException, request: HttpServletRequest
    ): ApiErrorResponse = ApiErrorResponse(
        timestamp = Date(),
        status = HttpStatus.BAD_REQUEST.value(),
        error = "Bad Request",
        message = ex.message ?: "Некорректное тело запроса",
        path = request.requestURI
    )

    @ExceptionHandler(AuthorizationDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleAuthorizationDenied(
        ex: AuthorizationDeniedException,
        request: HttpServletRequest
    ): ApiErrorResponse =
        ApiErrorResponse(
            timestamp = Date(),
            status = HttpStatus.FORBIDDEN.value(),
            error = "Forbidden",
            message = ex.message ?: "Доступ запрещён",
            path = request.requestURI
        )

    @ExceptionHandler(ExpiredJwtException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleExpiredJwt(
        ex: ExpiredJwtException,
        request: HttpServletRequest
    ): ApiErrorResponse =
        ApiErrorResponse(
            timestamp = Date(),
            status = HttpStatus.UNAUTHORIZED.value(),
            error = "Unauthorized",
            message = "JWT токен истёк",
            path = request.requestURI
        )

    @ExceptionHandler(SessionJoinRejectedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleSessionJoinRejected(
        ex: SessionJoinRejectedException,
        request: HttpServletRequest
    ): ApiErrorResponse =
        ApiErrorResponse(
            timestamp = Date(),
            status = HttpStatus.CONFLICT.value(),
            error = "Conflict",
            message = ex.message ?: "Невозможно присоединиться к сессии",
            path = request.requestURI
        )

}