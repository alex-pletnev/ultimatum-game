package edu.itmo.ultimatumgame.controllers

import edu.itmo.ultimatumgame.services.CsvService
import edu.itmo.ultimatumgame.services.StatsService
import edu.itmo.ultimatumgame.util.logger
import jakarta.persistence.EntityNotFoundException
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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

    private val log = logger()

    /**
     * Скачать CSV со всеми офферами указанной сессии.
     */
    // T-086: публичный read-only endpoint — летопись партии без JWT.
    @GetMapping("/{sessionId}/csv")
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
            log.debug("CSV download rejected for session {}: {}", sessionId, ex.message)
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
}
