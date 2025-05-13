package edu.itmo.ultimatum_game.controllers

import edu.itmo.ultimatum_game.dto.requests.CreateSessionRequest
import edu.itmo.ultimatum_game.dto.responses.SessionResponse
import edu.itmo.ultimatum_game.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatum_game.services.SessionService
import edu.itmo.ultimatum_game.util.logger
import edu.itmo.ultimatum_game.util.toUuidOrThrow
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/session")
@Validated
class SessionController(
    private val sessionService: SessionService,
) {

    private val logger = logger()

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSession(@RequestBody @Valid createSessionRequest: CreateSessionRequest): SessionWithTeamsAndMembersResponse {
        logger.info("Получен запрос на создание сессии: $createSessionRequest")
        val response = sessionService.createSession(createSessionRequest)
        logger.info("Создана сессия $response")
        return response
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLAYER', 'OBSERVER')")
    fun getSession(@PathVariable id: String): SessionResponse {
        logger.info("Получен запрос на получение сессии с id: $id")
        val response = sessionService.getSession(id.toUuidOrThrow())
        logger.info("По {} найдена сессия {}", id, response)
        return response
    }

    @GetMapping("/{id}/with-teams-and-members")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLAYER', 'OBSERVER')")
    fun getSessionWithTeamsAndMembers(@PathVariable id: String): SessionWithTeamsAndMembersResponse {
        logger.info("Получен запрос на получение сессии(с командами и игроками) с id: $id")
        val response = sessionService.getSessionWithTeamsAndMembers(id.toUuidOrThrow())
        logger.info("По id {} найдена сессия {}", id, response)
        return response
    }


    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PLAYER', 'OBSERVER')")
    fun getSessions(
        @RequestParam(required = false, defaultValue = "")
        @Size(max = 100, message = "Размер поискового запроса должен быть меньше 100 символов")
        s: String,
        @RequestParam(required = false, defaultValue = "0")
        page: Int,
        @RequestParam(required = false, defaultValue = "30")
        pageSize: Int,
    ): Page<SessionResponse> {
        logger.info("Получен запрос на получение сессий s=$s, page=$page, pageSize=$pageSize")
        val response = sessionService.getAllSessions(page, pageSize, s)
        return response
    }

    @PostMapping("/{sessionId}/join")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLAYER')")
    fun joinSession(
        @PathVariable sessionId: String,
        @RequestParam(required = false) teamId: String?
    ): SessionWithTeamsAndMembersResponse {
        val sessionUuid = sessionId.toUuidOrThrow()
        val teamUuid: UUID? = if (!teamId.isNullOrBlank()) teamId.toUuidOrThrow() else null
        logger.info("Запрос на подключение к сессии id: $sessionId")
        val response = sessionService.joinSession(sessionUuid, teamUuid)
        return response
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PLAYER', 'OBSERVER')")
    @PostMapping("/{sessionId}/join/observer")
    fun joinSessionAsObserver(@PathVariable sessionId: String): SessionWithTeamsAndMembersResponse {
        val sessionUuid = sessionId.toUuidOrThrow()
        logger.info("Запрос на подключение к сессии (как observer)")
        val response = sessionService.joinSessionAsObserver(sessionUuid)
        return response
    }


}