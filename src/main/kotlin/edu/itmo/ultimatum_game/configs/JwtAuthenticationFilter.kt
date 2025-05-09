package edu.itmo.ultimatum_game.configs

import edu.itmo.ultimatum_game.services.JwtService
import edu.itmo.ultimatum_game.services.UserService
import edu.itmo.ultimatum_game.util.logger
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

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
        if (authHeader == null || authHeader == "" || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Отсутствует или некорректный Authorization Header")
            filterChain.doFilter(request, response)
            return
        }
        val jwt: String = authHeader.substring(BEARER_PREFIX.length)
        val username: String = jwtService.extractUsername(jwt)

        if (username.isNotBlank() && SecurityContextHolder.getContext().authentication == null) {
            log.info("Попытка аутентификации пользователя с id=$username по токену")
            try {


                val userDetail = userService.getUserDetailService().invoke(UUID.fromString(username))
                if (jwtService.isTokenValid(jwt, userDetail)) {
                    log.info("Токен валиден для пользователя id=$username (${userDetail.role}) ")
                    val context = SecurityContextHolder.createEmptyContext()


                    val authorities = userDetail.authorities
                        .map { "ROLE_${it}" }
                        .map { SimpleGrantedAuthority(it) }
                    val authToken = UsernamePasswordAuthenticationToken(
                        userDetail,
                        null,
                        authorities
                    )
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                    context.authentication = authToken
                    SecurityContextHolder.setContext(context)


                } else log.warn("Невалидный токен для пользователя id=$username")
            } catch (e: Exception) {
                throw AccessDeniedException("В доступе отказано", e)
            }
        } else {
            if (username.isBlank()) log.warn("Не удалось извлечь username из токена")
            if (SecurityContextHolder.getContext().authentication != null) log.debug("Пользователь уже аутентифицирован, пропускаем фильтр")
        }
        filterChain.doFilter(request, response)
    }
}