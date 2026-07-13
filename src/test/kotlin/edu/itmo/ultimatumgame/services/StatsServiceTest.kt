package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.TestFixtures.offer
import edu.itmo.ultimatumgame.TestFixtures.round
import edu.itmo.ultimatumgame.TestFixtures.session
import edu.itmo.ultimatumgame.TestFixtures.sessionConfig
import edu.itmo.ultimatumgame.TestFixtures.team
import edu.itmo.ultimatumgame.TestFixtures.user
import edu.itmo.ultimatumgame.model.Decision
import edu.itmo.ultimatumgame.model.SessionType
import edu.itmo.ultimatumgame.repositories.DecisionRepository
import edu.itmo.ultimatumgame.repositories.OfferRepository
import edu.itmo.ultimatumgame.repositories.SessionRepository
import io.mockk.every
import io.mockk.mockk
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsServiceTest {

    private val sessionRepo = mockk<SessionRepository>()
    private val offerRepo = mockk<OfferRepository>()
    private val decisionRepo = mockk<DecisionRepository>()
    private val service = StatsService(sessionRepo, offerRepo, decisionRepo)

    @Test
    fun `getSessionStats — бросает EntityNotFoundException для несуществующей сессии`() {
        val id = UUID.randomUUID()
        every { sessionRepo.findById(id) } returns Optional.empty()

        assertThrows<EntityNotFoundException> { service.getSessionStats(id) }
    }

    @Test
    fun `getSessionStats — пустая сессия возвращает пустой offers`() {
        val s = session()
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns emptyList()
        every { decisionRepo.findAllBySessionIdWithRelations(s.id!!) } returns emptyList()

        val stats = service.getSessionStats(s.id!!)

        assertEquals(s.id, stats.sessionId)
        assertEquals(0, stats.decisionsCount)
        assertTrue(stats.offers.isEmpty())
    }

    @Test
    fun `FREE_FOR_ALL — proposerTeam и responderTeam всегда null`() {
        val a = user()
        val b = user()
        val s = session(members = mutableSetOf(a, b), config = sessionConfig(sessionType = SessionType.FREE_FOR_ALL))
        val r = round(session = s)
        val o = offer(proposer = a, responder = b, round = r)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(o)
        every { decisionRepo.findAllBySessionIdWithRelations(s.id!!) } returns emptyList()

        val stats = service.getSessionStats(s.id!!)

        assertEquals(1, stats.offers.size)
        val row = stats.offers[0]
        assertNull(row.proposerTeam)
        assertNull(row.responderTeam)
        assertNull(row.accepted)
    }

    @Test
    fun `TEAM_BATTLE — proposerTeam и responderTeam проставляются по членству`() {
        val a = user()
        val b = user()
        val tA = team(name = "A", members = mutableSetOf(a))
        val tB = team(name = "B", members = mutableSetOf(b))
        val s = session(
            members = mutableSetOf(a, b),
            teams = mutableSetOf(tA, tB),
            config = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numTeams = 2),
        )
        val r = round(session = s)
        val o = offer(proposer = a, responder = b, round = r)

        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(o)
        every { decisionRepo.findAllBySessionIdWithRelations(s.id!!) } returns emptyList()

        val stats = service.getSessionStats(s.id!!)
        val row = stats.offers[0]
        assertEquals("A", row.proposerTeam?.name)
        assertEquals("B", row.responderTeam?.name)
    }

    @Test
    fun `accepted проставляется из decision`() {
        val a = user()
        val b = user()
        val s = session(members = mutableSetOf(a, b))
        val r = round(session = s)
        val o = offer(proposer = a, responder = b, round = r)
        val d = Decision(
            id = UUID.randomUUID(),
            session = s,
            round = r,
            responder = b,
            offer = o,
            decision = true,
        )
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(o)
        every { decisionRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(d)

        val stats = service.getSessionStats(s.id!!)
        assertEquals(true, stats.offers[0].accepted)
        assertEquals(1, stats.decisionsCount)
    }

    @Test
    fun `accept — proposer получает roundSum минус offer, responder получает offer`() {
        val a = user(nickname = "alice")
        val b = user(nickname = "bob")
        val s = session(members = mutableSetOf(a, b), config = sessionConfig(roundSum = 100))
        val r = round(session = s, roundNumber = 1)
        val o = offer(proposer = a, responder = b, round = r, offerValue = 30)
        val d = Decision(
            id = UUID.randomUUID(),
            session = s,
            round = r,
            responder = b,
            offer = o,
            decision = true,
        )
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(o)
        every { decisionRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(d)

        val stats = service.getSessionStats(s.id!!)

        val row = stats.offers[0]
        assertEquals(70, row.proposerScore)
        assertEquals(30, row.responderScore)
        val alice = stats.score.players.first { it.nickname == "alice" }
        val bob = stats.score.players.first { it.nickname == "bob" }
        assertEquals(70, alice.score)
        assertEquals(30, bob.score)
    }

    @Test
    fun `reject — обе стороны получают 0`() {
        val a = user(nickname = "alice")
        val b = user(nickname = "bob")
        val s = session(members = mutableSetOf(a, b), config = sessionConfig(roundSum = 100))
        val r = round(session = s, roundNumber = 1)
        val o = offer(proposer = a, responder = b, round = r, offerValue = 40)
        val d = Decision(
            id = UUID.randomUUID(),
            session = s,
            round = r,
            responder = b,
            offer = o,
            decision = false,
        )
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(o)
        every { decisionRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(d)

        val stats = service.getSessionStats(s.id!!)

        val row = stats.offers[0]
        assertEquals(0, row.proposerScore)
        assertEquals(0, row.responderScore)
        assertEquals(0, stats.score.players.sumOf { it.score })
    }

    @Test
    fun `no decision yet — score = 0, не начисляется`() {
        val a = user(nickname = "alice")
        val b = user(nickname = "bob")
        val s = session(members = mutableSetOf(a, b), config = sessionConfig(roundSum = 100))
        val r = round(session = s, roundNumber = 1)
        val o = offer(proposer = a, responder = b, round = r, offerValue = 50)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(o)
        every { decisionRepo.findAllBySessionIdWithRelations(s.id!!) } returns emptyList()

        val stats = service.getSessionStats(s.id!!)

        val row = stats.offers[0]
        assertEquals(0, row.proposerScore)
        assertEquals(0, row.responderScore)
        assertEquals(0, stats.score.players.sumOf { it.score })
    }

    @Test
    fun `TEAM_BATTLE — team scores суммируются по членству`() {
        val a = user(nickname = "alice")
        val b = user(nickname = "bob")
        val c = user(nickname = "carol")
        val d = user(nickname = "dave")
        val tA = team(name = "A", members = mutableSetOf(a, c))
        val tB = team(name = "B", members = mutableSetOf(b, d))
        val s = session(
            members = mutableSetOf(a, b, c, d),
            teams = mutableSetOf(tA, tB),
            config = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numTeams = 2, roundSum = 100),
        )
        val r1 = round(session = s, roundNumber = 1)
        val r2 = round(session = s, roundNumber = 2)
        // a (team A) → b (team B), offer 40, accept: A += 60, B += 40
        val o1 = offer(proposer = a, responder = b, round = r1, offerValue = 40)
        val d1 = Decision(
            id = UUID.randomUUID(),
            session = s,
            round = r1,
            responder = b,
            offer = o1,
            decision = true,
        )
        // c (team A) → d (team B), offer 20, reject: 0/0
        val o2 = offer(proposer = c, responder = d, round = r2, offerValue = 20)
        val d2 = Decision(
            id = UUID.randomUUID(),
            session = s,
            round = r2,
            responder = d,
            offer = o2,
            decision = false,
        )

        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(o1, o2)
        every { decisionRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(d1, d2)

        val stats = service.getSessionStats(s.id!!)

        val teamA = stats.score.teams.first { it.name == "A" }
        val teamB = stats.score.teams.first { it.name == "B" }
        assertEquals(60, teamA.score)
        assertEquals(40, teamB.score)
        assertEquals(2, stats.score.teams.size)
        assertEquals(100, stats.score.roundSum)
    }

    @Test
    fun `FREE_FOR_ALL — teams в score пустые`() {
        val a = user(nickname = "alice")
        val b = user(nickname = "bob")
        val s = session(members = mutableSetOf(a, b), config = sessionConfig(sessionType = SessionType.FREE_FOR_ALL))
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns emptyList()
        every { decisionRepo.findAllBySessionIdWithRelations(s.id!!) } returns emptyList()

        val stats = service.getSessionStats(s.id!!)

        assertTrue(stats.score.teams.isEmpty())
        assertEquals(2, stats.score.players.size)
        assertTrue(stats.score.players.all { it.teamId == null && it.teamName == null })
    }

    @Test
    fun `offers сортируются по roundNumber`() {
        val a = user()
        val b = user()
        val c = user()
        val d = user()
        val s = session(members = mutableSetOf(a, b, c, d))
        val r1 = round(session = s, roundNumber = 1)
        val r2 = round(session = s, roundNumber = 2)
        val r3 = round(session = s, roundNumber = 3)
        val o3 = offer(proposer = a, responder = b, round = r3)
        val o1 = offer(proposer = c, responder = d, round = r1)
        val o2 = offer(proposer = b, responder = a, round = r2)

        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(o3, o1, o2)
        every { decisionRepo.findAllBySessionIdWithRelations(s.id!!) } returns emptyList()

        val stats = service.getSessionStats(s.id!!)
        assertEquals(listOf(1, 2, 3), stats.offers.map { it.roundNumber })
    }
}
