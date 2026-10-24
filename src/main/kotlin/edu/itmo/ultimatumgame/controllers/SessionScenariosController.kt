package edu.itmo.ultimatumgame.controllers

import edu.itmo.ultimatumgame.dto.requests.SoloSessionRequest
import edu.itmo.ultimatumgame.dto.responses.SessionResponse
import edu.itmo.ultimatumgame.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatumgame.services.SessionScenariosService
import edu.itmo.ultimatumgame.services.SessionService
import edu.itmo.ultimatumgame.util.toUuidOrThrow
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/session")
class SessionScenariosController(
    private val scenarios: SessionScenariosService,
    private val sessionService: SessionService,
) {
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLAYER', 'OBSERVER')")
    fun mySessions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") pageSize: Int,
    ): Page<SessionResponse> = sessionService.getMySessions(page, pageSize)

    @PostMapping("/solo")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    fun solo(@Valid @RequestBody request: SoloSessionRequest): SessionWithTeamsAndMembersResponse =
        scenarios.solo(request)

    @PostMapping("/{id}/npcs/random")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN') and @sessionAccess.isHost(#id, authentication)")
    fun fillRandomNpcs(@PathVariable id: String): SessionWithTeamsAndMembersResponse =
        scenarios.fillRandomNpcs(id.toUuidOrThrow())

    @PostMapping("/{id}/rematch")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN') and @sessionAccess.isHost(#id, authentication)")
    fun rematch(@PathVariable id: String): SessionWithTeamsAndMembersResponse =
        scenarios.rematch(id.toUuidOrThrow())
}
