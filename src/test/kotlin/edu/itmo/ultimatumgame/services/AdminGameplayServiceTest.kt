package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.TestFixtures.round
import edu.itmo.ultimatumgame.TestFixtures.session
import edu.itmo.ultimatumgame.model.RoundPhase
import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.model.SessionState
import edu.itmo.ultimatumgame.repositories.SessionRepository
import edu.itmo.ultimatumgame.util.DomainEventLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class AdminGameplayServiceTest {

    private val sessionService = mockk<SessionService>()
    private val sessionRepository = mockk<SessionRepository>()
    private val eventPublisher = mockk<EventPublisherService>(relaxUnitFun = true)
    private val domainEventLogger = mockk<DomainEventLogger>(relaxUnitFun = true)
    private val service = AdminGameplayService(sessionService, sessionRepository, eventPublisher, domainEventLogger)

    private fun stubSaveIdentity() {
        every { sessionRepository.save(any<Session>()) } answers { firstArg() }
    }

    @Test
    fun `startSession — переводит state в RUNNING, current в round 1 с фазой WAIT_OFFERS, закрывает openToConnect`() {
        val r1 = round(roundNumber = 1)
        val r2 = round(roundNumber = 2)
        val s = session()
        s.rounds = mutableSetOf(r1, r2)
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubSaveIdentity()

        service.startSession(s.id!!)

        assertEquals(SessionState.RUNNING, s.state)
        assertSame(r1, s.currentRound)
        assertEquals(RoundPhase.WAIT_OFFERS, r1.roundPhase)
        assertFalse(s.openToConnect)
        verify { eventPublisher.publishSessionStatus(s.id!!, s) }
    }

    @Test
    fun `closeSession — openToConnect=false + публикация статуса`() {
        val s = session(openToConnect = true)
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubSaveIdentity()

        service.closeSession(s.id!!)

        assertFalse(s.openToConnect)
        verify { eventPublisher.publishSessionStatus(s.id!!, s) }
    }

    @Test
    fun `openSession — openToConnect=true + публикация статуса`() {
        val s = session(openToConnect = false)
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubSaveIdentity()

        service.openSession(s.id!!)

        assertEquals(true, s.openToConnect)
        verify { eventPublisher.publishSessionStatus(s.id!!, s) }
    }

    @Test
    fun `abortSession — state=ABORTED, openToConnect=false, публикует roundStatus если есть текущий раунд`() {
        val r = round()
        val s = session(currentRound = r, openToConnect = true)
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubSaveIdentity()

        service.abortSession(s.id!!)

        assertEquals(SessionState.ABORTED, s.state)
        assertFalse(s.openToConnect)
        verify { eventPublisher.publishRoundStatus(s.id!!, r) }
    }

    @Test
    fun `abortSession без текущего раунда — не публикует RoundStatus`() {
        val s = session(currentRound = null, openToConnect = true)
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubSaveIdentity()

        service.abortSession(s.id!!)

        assertEquals(SessionState.ABORTED, s.state)
        verify(exactly = 0) { eventPublisher.publishRoundStatus(any(), any()) }
    }

    @Test
    fun `startNextRound — переходит к следующему раунду с фазой WAIT_OFFERS`() {
        val r1 = round(roundNumber = 1, roundPhase = RoundPhase.ALL_DECISIONS_RECEIVED)
        val r2 = round(roundNumber = 2, roundPhase = RoundPhase.CREATED)
        val s = session(currentRound = r1)
        s.rounds = mutableSetOf(r1, r2)
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubSaveIdentity()

        service.startNextRound(s.id!!)

        assertEquals(RoundPhase.FINISHED, r1.roundPhase)
        assertSame(r2, s.currentRound)
        assertEquals(RoundPhase.WAIT_OFFERS, r2.roundPhase)
        verify { eventPublisher.publishRoundStatus(s.id!!, r2) }
    }

    @Test
    fun `startNextRound на последнем раунде — сессия FINISHED, currentRound=остаётся_текущий_с_фазой_FINISHED`() {
        val r1 = round(roundNumber = 1, roundPhase = RoundPhase.ALL_DECISIONS_RECEIVED)
        val s = session(currentRound = r1)
        s.rounds = mutableSetOf(r1)
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubSaveIdentity()

        service.startNextRound(s.id!!)

        assertEquals(SessionState.FINISHED, s.state)
        assertEquals(RoundPhase.FINISHED, r1.roundPhase)
        assertSame(r1, s.currentRound)
        verify { eventPublisher.publishRoundStatus(s.id!!, r1) }
    }

    @Test
    fun `startNextRound бросает если currentRound null`() {
        val s = session(currentRound = null)
        every { sessionService.getSessionEntity(s.id!!) } returns s

        assertThrows<IllegalStateException> { service.startNextRound(s.id!!) }
    }

    @Test
    fun `abortCurrentRound — переводит фазу в ABORTED, публикует roundStatus, эмитит RoundAborted`() {
        val r = round(roundNumber = 1, roundPhase = RoundPhase.WAIT_OFFERS)
        val s = session(state = SessionState.RUNNING, currentRound = r)
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubSaveIdentity()

        service.abortCurrentRound(s.id!!)

        assertEquals(RoundPhase.ABORTED, r.roundPhase)
        verify { eventPublisher.publishRoundStatus(s.id!!, r) }
    }

    @Test
    fun `abortCurrentRound — бросает если сессия не RUNNING`() {
        val r = round(roundPhase = RoundPhase.WAIT_OFFERS)
        val s = session(state = SessionState.CREATED, currentRound = r)
        every { sessionService.getSessionEntity(s.id!!) } returns s

        assertThrows<IllegalStateException> { service.abortCurrentRound(s.id!!) }
    }

    @Test
    fun `abortCurrentRound — бросает если нет активного раунда`() {
        val s = session(state = SessionState.RUNNING, currentRound = null)
        every { sessionService.getSessionEntity(s.id!!) } returns s

        assertThrows<IllegalStateException> { service.abortCurrentRound(s.id!!) }
    }

    @Test
    fun `abortCurrentRound — бросает если раунд уже FINISHED`() {
        val r = round(roundPhase = RoundPhase.FINISHED)
        val s = session(state = SessionState.RUNNING, currentRound = r)
        every { sessionService.getSessionEntity(s.id!!) } returns s

        assertThrows<IllegalStateException> { service.abortCurrentRound(s.id!!) }
    }

    @Test
    fun `abortCurrentRound — бросает если раунд уже ABORTED (идемпотентность)`() {
        val r = round(roundPhase = RoundPhase.ABORTED)
        val s = session(state = SessionState.RUNNING, currentRound = r)
        every { sessionService.getSessionEntity(s.id!!) } returns s

        assertThrows<IllegalStateException> { service.abortCurrentRound(s.id!!) }
    }

    @Test
    fun `startNextRound после ABORTED — сохраняет фазу ABORTED, переходит к следующему раунду`() {
        val r1 = round(roundNumber = 1, roundPhase = RoundPhase.ABORTED)
        val r2 = round(roundNumber = 2, roundPhase = RoundPhase.CREATED)
        val s = session(state = SessionState.RUNNING, currentRound = r1)
        s.rounds = mutableSetOf(r1, r2)
        every { sessionService.getSessionEntity(s.id!!) } returns s
        stubSaveIdentity()

        service.startNextRound(s.id!!)

        assertEquals(RoundPhase.ABORTED, r1.roundPhase, "ABORTED сохраняется, не переписывается")
        assertSame(r2, s.currentRound)
        assertEquals(RoundPhase.WAIT_OFFERS, r2.roundPhase)
    }
}
