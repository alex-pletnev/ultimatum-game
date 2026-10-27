@file:Suppress("ReturnCount")

package edu.itmo.ultimatumgame.configs

import edu.itmo.ultimatumgame.exceptions.IdNotFoundException
import edu.itmo.ultimatumgame.services.JwtService
import edu.itmo.ultimatumgame.services.UserService
import edu.itmo.ultimatumgame.util.logger
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private const val MDC_USER_ID = "userId"
private const val MDC_ROLE = "role"

const val HEADER_AUTHORIZATION = "Authorization"
const val BEARER_PREFIX = "Bearer "

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userService: UserService,
) : OncePerRequestFilter() {

    private val log = logger()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader(HEADER_AUTHORIZATION)
        if (authHeader.isNullOrEmpty() || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Отсутствует или некорректный Authorization Header")
            filterChain.doFilter(request, response)
            return
        }
        try {
            val jwt = authHeader.substring(BEARER_PREFIX.length)
            val username = jwtService.extractUsername(jwt)
            val user = userService.getUserDetailService().invoke(UUID.fromString(username))
            if (!jwtService.isTokenValid(jwt, user)) {
                unauthorized(response)
                return
            }
            val auth = UsernamePasswordAuthenticationToken(
                user,
                null,
                user.authorities.map { SimpleGrantedAuthority("ROLE_$it") }
            )
            auth.details = WebAuthenticationDetailsSource().buildDetails(request)
            SecurityContextHolder.getContext().authentication = auth
            MDC.put(MDC_USER_ID, username)
            MDC.put(MDC_ROLE, user.role.name)
        } catch (_: JwtException) {
            unauthorized(response)
            return
        } catch (_: IllegalArgumentException) {
            unauthorized(response)
            return
        } catch (_: IdNotFoundException) {
            unauthorized(response)
            return
        }
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_USER_ID)
            MDC.remove(MDC_ROLE)
        }
    }

    private fun unauthorized(response: HttpServletResponse) {
        SecurityContextHolder.clearContext()
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write("""{"status":401,"message":"Вход истёк. Обновите авторизацию."}""")
    }
}
