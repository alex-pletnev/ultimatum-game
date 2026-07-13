package edu.itmo.ultimatumgame.controllers

import edu.itmo.ultimatumgame.services.CsvService
import edu.itmo.ultimatumgame.services.StatsService
import jakarta.persistence.EntityNotFoundException
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/statistics")
class StatisticController(
    private val statsService: StatsService,
    private val csvService: CsvService,
) {

    /**
     * Скачать CSV со всеми офферами указанной сессии.
     */
    @GetMapping("/{sessionId}/csv")
    @PreAuthorize("hasAnyRole('ADMIN','PLAYER','OBSERVER')")
    fun downloadCsv(@PathVariable sessionId: UUID): ResponseEntity<ByteArrayResource> =
        try {
            val stats = statsService.getSessionStats(sessionId)
            val bytes = csvService.toCsv(stats)
            ResponseEntity.ok()
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"session-$sessionId-stats.csv\""
                )
                .contentLength(bytes.size.toLong())
                .contentType(MediaType.TEXT_PLAIN) // text/csv → некоторые браузеры лучше обрабатывают text/plain
                .body(ByteArrayResource(bytes))
        } catch (ex: EntityNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
}
