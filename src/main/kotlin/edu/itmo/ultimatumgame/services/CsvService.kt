@file:Suppress("SpreadOperator")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.responses.SessionStatsDto
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@Service
class CsvService {

    /**
     * Преобразовать статистику сессии в CSV-файл.
     * @return массив байт, готовый к отправке клиенту
     */
    fun toCsv(stats: SessionStatsDto): ByteArray {
        val header = arrayOf(
            "offerId",
            "roundNumber",
            "amount",
            "proposerId", "proposerNickname",
            "responderId", "responderNickname",
            "proposerTeamId", "proposerTeamName",
            "responderTeamId", "responderTeamName",
            "accepted",
            "timestamp"
        )

        val baos = ByteArrayOutputStream()
        CSVPrinter(
            OutputStreamWriter(baos, StandardCharsets.UTF_8),
            CSVFormat.DEFAULT.builder()
                .setHeader(*header)
                .build()
        ).use { printer ->
            stats.offers.forEach { o ->
                printer.printRecord(
                    o.offerId,
                    o.roundNumber,
                    o.amount,
                    o.proposer.id, o.proposer.nickname,
                    o.responder?.id, o.responder?.nickname,
                    o.proposerTeam?.id, o.proposerTeam?.name,
                    o.responderTeam?.id, o.responderTeam?.name,
                    o.accepted,
                    o.timestamp
                )
            }
        }
        return baos.toByteArray()
    }
}
