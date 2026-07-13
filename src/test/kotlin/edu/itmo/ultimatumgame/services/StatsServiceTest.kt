@file:Suppress("NoSemicolons")

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
        every { decisionRepo.findBySessionId(s.id!!) } returns emptyList()

        val stats = service.getSessionStats(s.id!!)

        assertEquals(s.id, stats.sessionId)
        assertEquals(0, stats.decisionsCount)
        assertTrue(stats.offers.isEmpty())
    }

    @Test
    fun `FREE_FOR_ALL — proposerTeam и responderTeam всегда null`() {
        val a = user();
        val b = user()
        val s = session(members = mutableSetOf(a, b), config = sessionConfig(sessionType = SessionType.FREE_FOR_ALL))
        val r = round(session = s)
        val o = offer(proposer = a, responder = b, round = r)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { offerRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(o)
        every { decisionRepo.findBySessionId(s.id!!) } returns emptyList()

        val stats = service.getSessionStats(s.id!!)

        assertEquals(1, stats.offers.size)
        val row = stats.offers[0]
        assertNull(row.proposerTeam)
        assertNull(row.responderTeam)
        assertNull(row.accepted)
    }

    @Test
    fun `TEAM_BATTLE — proposerTeam и responderTeam проставляются по членству`() {
        val a = user();
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
        every { decisionRepo.findBySessionId(s.id!!) } returns emptyList()

        val stats = service.getSessionStats(s.id!!)
        val row = stats.offers[0]
        assertEquals("A", row.proposerTeam?.name)
        assertEquals("B", row.responderTeam?.name)
    }

    @Test
    fun `accepted проставляется из decision`() {
        val a = user();
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
        every { decisionRepo.findBySessionId(s.id!!) } returns listOf(d)

        val stats = service.getSessionStats(s.id!!)
        assertEquals(true, stats.offers[0].accepted)
        assertEquals(1, stats.decisionsCount)
    }

    @Test
    fun `offers сортируются по roundNumber`() {
        val a = user();
        val b = user();
        val c = user();
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
        every { decisionRepo.findBySessionId(s.id!!) } returns emptyList()

        val stats = service.getSessionStats(s.id!!)
        assertEquals(listOf(1, 2, 3), stats.offers.map { it.roundNumber })
    }
}
