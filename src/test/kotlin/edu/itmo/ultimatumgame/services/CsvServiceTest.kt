package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.responses.OfferStatsDto
import edu.itmo.ultimatumgame.dto.responses.SessionScoreDto
import edu.itmo.ultimatumgame.dto.responses.SessionStatsDto
import edu.itmo.ultimatumgame.dto.responses.TeamInfo
import edu.itmo.ultimatumgame.dto.responses.UserInfo
import edu.itmo.ultimatumgame.model.SessionState
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvServiceTest {

    private val service = CsvService()

    private fun statsWith(offers: List<OfferStatsDto>) = SessionStatsDto(
        sessionId = UUID.randomUUID(),
        displayName = "s",
        state = SessionState.FINISHED,
        createdAt = Date(0),
        totalRounds = 1,
        decisionsCount = offers.count { it.accepted != null },
        offers = offers,
        score = SessionScoreDto(roundSum = 100, roundsCompleted = 0, players = emptyList(), teams = emptyList()),
    )

    @Test
    fun `пустая сессия — CSV содержит только header`() {
        val csv = service.toCsv(statsWith(emptyList())).toString(Charsets.UTF_8).trim()
        val lines = csv.split("\r\n", "\n").filter { it.isNotBlank() }
        assertEquals(1, lines.size, "должен быть только header")
        assertTrue(lines[0].contains("offerId"), "header содержит offerId")
        assertTrue(lines[0].contains("responderTeamName"), "header содержит responderTeamName")
    }

    @Test
    fun `запись с ответившим респондентом и командами`() {
        val proposer = UserInfo(UUID.randomUUID(), "Alice")
        val responder = UserInfo(UUID.randomUUID(), "Bob")
        val tA = TeamInfo(UUID.randomUUID(), "Alpha")
        val tB = TeamInfo(UUID.randomUUID(), "Bravo")
        val o = OfferStatsDto(
            offerId = UUID.randomUUID(),
            amount = 42,
            proposer = proposer,
            responder = responder,
            proposerTeam = tA,
            responderTeam = tB,
            accepted = true,
            proposerScore = 58,
            responderScore = 42,
            roundNumber = 2,
            timestamp = Date(0),
        )

        val csv = service.toCsv(statsWith(listOf(o))).toString(Charsets.UTF_8)
        assertTrue(csv.contains("Alice"))
        assertTrue(csv.contains("Bob"))
        assertTrue(csv.contains("Alpha"))
        assertTrue(csv.contains("Bravo"))
        assertTrue(csv.contains(",42,"))
        assertTrue(csv.contains("true"))
        assertTrue(csv.contains(",58,42,"), "scoring колонки в CSV")
        assertTrue(csv.contains("proposerScore"), "header содержит proposerScore")
    }

    @Test
    fun `запись без responder — пустые колонки для responder полей`() {
        val o = OfferStatsDto(
            offerId = UUID.randomUUID(),
            amount = 10,
            proposer = UserInfo(UUID.randomUUID(), "A"),
            responder = null,
            proposerTeam = null,
            responderTeam = null,
            accepted = null,
            proposerScore = 0,
            responderScore = 0,
            roundNumber = 1,
            timestamp = Date(0),
        )

        val csv = service.toCsv(statsWith(listOf(o))).toString(Charsets.UTF_8)
        val dataLine = csv.split("\r\n", "\n").drop(1).first()
        val cells = dataLine.split(",")
        assertEquals("A", cells[4], "proposerNickname на позиции 4")
        assertEquals("", cells[5], "responderId пуст")
        assertEquals("", cells[6], "responderNickname пуст")
        assertEquals("", cells[11], "accepted пуст")
        assertEquals("0", cells[12], "proposerScore = 0 при отсутствии decision")
        assertEquals("0", cells[13], "responderScore = 0 при отсутствии decision")
    }

    @Test
    fun `никнейм с запятой экранируется (заключается в кавычки)`() {
        val o = OfferStatsDto(
            offerId = UUID.randomUUID(),
            amount = 1,
            proposer = UserInfo(UUID.randomUUID(), "Comma,Guy"),
            responder = null,
            proposerTeam = null,
            responderTeam = null,
            accepted = null,
            proposerScore = 0,
            responderScore = 0,
            roundNumber = 1,
            timestamp = Date(0),
        )
        val csv = service.toCsv(statsWith(listOf(o))).toString(Charsets.UTF_8)
        assertTrue(csv.contains("\"Comma,Guy\""), "запятая в никнейме должна быть заэкранирована кавычками")
    }
}
