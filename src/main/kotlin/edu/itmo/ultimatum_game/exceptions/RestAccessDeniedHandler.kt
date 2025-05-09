package edu.itmo.ultimatum_game.exceptions

import com.fasterxml.jackson.databind.ObjectMapper
import edu.itmo.ultimatum_game.dto.responses.ApiErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import java.util.*

@Component
class RestAccessDeniedHandler(
    private val objectMapper: ObjectMapper
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        accessDeniedException: AccessDeniedException?
    ) {
        val error = ApiErrorResponse(
            timestamp = Date(),
            status = HttpStatus.FORBIDDEN.value(),
            error = HttpStatus.FORBIDDEN.reasonPhrase,
            message = accessDeniedException?.message ?: "Доступ запрещён",
            path = request?.servletPath ?: ""
        )

        response?.status = HttpStatus.FORBIDDEN.value()
        response?.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response?.writer, error)
    }
}