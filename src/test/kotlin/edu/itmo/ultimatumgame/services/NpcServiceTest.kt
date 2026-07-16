package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.TestFixtures
import edu.itmo.ultimatumgame.TestFixtures.offer
import edu.itmo.ultimatumgame.TestFixtures.session
import edu.itmo.ultimatumgame.TestFixtures.sessionConfig
import edu.itmo.ultimatumgame.TestFixtures.user
import edu.itmo.ultimatumgame.dto.responses.SessionScoreDto
import edu.itmo.ultimatumgame.dto.responses.SessionStatsDto
import edu.itmo.ultimatumgame.model.Decision
import edu.itmo.ultimatumgame.model.NpcParams
import edu.itmo.ultimatumgame.model.NpcProfile
import edu.itmo.ultimatumgame.model.NpcStrategy
import edu.itmo.ultimatumgame.model.Offer
import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.model.RoundPhase
import edu.itmo.ultimatumgame.repositories.DecisionRepository
import edu.itmo.ultimatumgame.repositories.NpcProfileRepository
import edu.itmo.ultimatumgame.repositories.OfferRepository
import edu.itmo.ultimatumgame.repositories.RoundRepository
import edu.itmo.ultimatumgame.repositories.SessionRepository
import edu.itmo.ultimatumgame.util.DomainEventLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpcServiceTest {

    private val npcProfileRepo = mockk<NpcProfileRepository>()
    private val offerRepo = mockk<OfferRepository>()
    private val decisionRepo = mockk<DecisionRepository>()
    private val roundRepo = mockk<RoundRepository>()
    private val sessionRepo = mockk<SessionRepository>()
    private val eventPublisher = mockk<EventPublisherService>(relaxUnitFun = true)
    private val domainEventLogger = mockk<DomainEventLogger>(relaxUnitFun = true)
    private val coreGameplayService = mockk<CoreGameplayService>(relaxUnitFun = true)
    private val statsService = mockk<StatsService>()
    private val adminGameplayService = mockk<AdminGameplayService>(relaxUnitFun = true)
    private val userRepository = mockk<edu.itmo.ultimatumgame.repositories.UserRepository>()

    init {
        every { roundRepo.save<edu.itmo.ultimatumgame.model.Round>(any()) } answers { firstArg() }
        every { sessionRepo.save<edu.itmo.ultimatumgame.model.Session>(any()) } answers { firstArg() }
    }

    private val service = NpcService(
        npcProfileRepo,
        offerRepo,
        decisionRepo,
        roundRepo,
        sessionRepo,
        eventPublisher,
        domainEventLogger,
        coreGameplayService,
        statsService,
        adminGameplayService,
        userRepository,
    )

    private fun profile(user: edu.itmo.ultimatumgame.model.User, strategy: NpcStrategy, params: NpcParams): NpcProfile =
        NpcProfile(id = UUID.randomUUID(), user = user, strategy = strategy, params = params, seed = 42L)

    @Test
    fun `playOffers — сохраняет offer для NPC и не трогает людей`() {
        val human = user(role = Role.PLAYER)
        val npc = user(role = Role.NPC)
        val cfg = sessionConfig(numPlayers = 2, roundSum = 100)
        val s = session(members = mutableSetOf(human, npc), config = cfg)
        val r = TestFixtures.round(session = s, roundNumber = 1, roundPhase = RoundPhase.WAIT_OFFERS)
        s.currentRound = r
        every { npcProfileRepo.findByUserId(npc.id!!) } returns profile(npc, NpcStrategy.FAIR, NpcParams.Fair())
        val saved = slot<Offer>()
        every { offerRepo.save(capture(saved)) } answers { firstArg<Offer>().apply { id = UUID.randomUUID() } }

        service.playOffers(r)

        assertEquals(1, r.offers.size)
        assertEquals(npc.id, saved.captured.proposer?.id)
        // Fair(roundSum=100) → 50
        assertEquals(50, saved.captured.offerValue)
        verify { eventPublisher.publishOfferCreated(s.id!!, any()) }
    }

    @Test
    fun `playOffers — no-op при phase != WAIT_OFFERS`() {
        val npc = user(role = Role.NPC)
        val s = session(members = mutableSetOf(npc))
        val r = TestFixtures.round(session = s, roundPhase = RoundPhase.OFFERS_SENT)

        service.playOffers(r)

        verify(exactly = 0) { offerRepo.save(any()) }
    }

    @Test
    fun `playOffers — no-op если NPC уже сделал offer (retry safety)`() {
        val npc = user(role = Role.NPC)
        val s = session(members = mutableSetOf(npc), config = sessionConfig(numPlayers = 1))
        val r = TestFixtures.round(session = s, roundPhase = RoundPhase.WAIT_OFFERS)
        s.currentRound = r
        r.offers += offer(proposer = npc, session = s, round = r)

        service.playOffers(r)

        verify(exactly = 0) { offerRepo.save(any()) }
    }

    @Test
    fun `playOffers — fallback FAIR если стратегия бросает`() {
        // сломанная стратегия: profile.strategy=SELFISH, но params несовместим (кастом провалится)
        val npc = user(role = Role.NPC)
        val cfg = sessionConfig(numPlayers = 1, roundSum = 100)
        val s = session(members = mutableSetOf(npc), config = cfg)
        val r = TestFixtures.round(session = s, roundPhase = RoundPhase.WAIT_OFFERS)
        s.currentRound = r
        val brokenProfile = NpcProfile(
            id = UUID.randomUUID(),
            user = npc,
            strategy = NpcStrategy.SELFISH,
            params = NpcParams.Fair(), // wrong type → cast fails
        )
        every { npcProfileRepo.findByUserId(npc.id!!) } returns brokenProfile
        val saved = slot<Offer>()
        every { offerRepo.save(capture(saved)) } answers { firstArg<Offer>().apply { id = UUID.randomUUID() } }
        every { coreGameplayService.initWaitDecisionsPhase(any()) } answers {}
        every { statsService.getSessionStats(any()) } returns SessionStatsDto(
            sessionId = s.id!!,
            displayName = "s",
            state = edu.itmo.ultimatumgame.model.SessionState.CREATED,
            createdAt = java.util.Date(0),
            totalRounds = 3,
            decisionsCount = 0,
            offers = emptyList(),
            score = SessionScoreDto(
                roundSum = 100,
                roundsCompleted = 0,
                players = emptyList(),
                teams = emptyList(),
            ),
        )

        service.playOffers(r)

        // Fair fallback → 50
        assertEquals(50, saved.captured.offerValue)
        verify { domainEventLogger.emit(match { it.type == "npc.strategy.failed" }) }
    }

    @Test
    fun `playDecisions — сохраняет decision для каждого NPC-члена`() {
        val proposer = user(role = Role.PLAYER)
        val npc = user(role = Role.NPC)
        val cfg = sessionConfig(numPlayers = 2, roundSum = 100)
        val s = session(members = mutableSetOf(proposer, npc), config = cfg)
        val r = TestFixtures.round(session = s, roundPhase = RoundPhase.OFFERS_SENT)
        s.currentRound = r
        val incoming = offer(proposer = proposer, responder = npc, session = s, round = r, offerValue = 30)
        r.offers += incoming
        every { npcProfileRepo.findByUserId(npc.id!!) } returns profile(npc, NpcStrategy.FAIR, NpcParams.Fair())
        val saved = slot<Decision>()
        every { decisionRepo.save(capture(saved)) } answers { firstArg<Decision>().apply { id = UUID.randomUUID() } }

        service.playDecisions(r)

        // human offer'а нет → всего 1 decision (NPC решает)
        assertEquals(1, r.decisions.size)
        assertEquals(npc.id, saved.captured.responder?.id)
        // Fair(fairnessThreshold=0.30, roundSum=100) → threshold=30, incoming=30 → accept
        assertTrue(saved.captured.decision)
    }
}
