package edu.itmo.ultimatumgame.controllers

import edu.itmo.ultimatumgame.dto.requests.CreateNpcRequest
import edu.itmo.ultimatumgame.dto.responses.NpcProfileResponse
import edu.itmo.ultimatumgame.services.NpcService
import edu.itmo.ultimatumgame.util.logger
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/npc")
@PreAuthorize("hasRole('ADMIN')")
class NpcController(
    private val npcService: NpcService,
) {

    private val logger = logger()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateNpcRequest): NpcProfileResponse {
        logger.info("POST /npc — strategy={} nickname={}", req.strategy, req.nickname)
        return npcService.create(req)
    }

    @GetMapping
    fun list(): List<NpcProfileResponse> = npcService.list()

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): NpcProfileResponse = npcService.get(id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        logger.info("DELETE /npc/{}", id)
        npcService.delete(id)
    }
}
