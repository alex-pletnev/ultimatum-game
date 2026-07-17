package edu.itmo.ultimatumgame.configs

import edu.itmo.ultimatumgame.exceptions.RestAccessDeniedHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
class SecurityConfiguration(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val restAccessDeniedHandler: RestAccessDeniedHandler,
    @Value("\${app.cors.origins:http://localhost:[*]}") private val corsOriginsCsv: String,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                it.requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/auth/**").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**").permitAll()
                    .requestMatchers("/springwolf/**").permitAll()
                    // T-086: публичные read-only летописи партии (без JWT)
                    .requestMatchers(HttpMethod.GET, "/statistics/*/csv").permitAll()
                    .requestMatchers(HttpMethod.GET, "/session/*/with-teams-and-members").permitAll()
                    .requestMatchers(HttpMethod.POST, "/session").hasAnyRole("ADMIN")
                    .requestMatchers("/ws/**").permitAll()
                    .anyRequest().authenticated()
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptions ->
                exceptions.accessDeniedHandler(restAccessDeniedHandler)
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = parseOrigins(corsOriginsCsv)
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    private fun parseOrigins(csv: String): List<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
