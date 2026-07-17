// T-093 follow-up (T-095): файл разросся после T-087+T-093 — детект жалуется на LargeClass.
// Split на несколько классов — отдельная задача.
@file:Suppress("LargeClass")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.TestFixtures
import edu.itmo.ultimatumgame.TestFixtures.offer
import edu.itmo.ultimatumgame.TestFixtures.session
import edu.itmo.ultimatumgame.TestFixtures.sessionConfig
import edu.itmo.ultimatumgame.TestFixtures.team
import edu.itmo.ultimatumgame.TestFixtures.user
import edu.itmo.ultimatumgame.dto.requests.BulkNpcsRequest
import edu.itmo.ultimatumgame.dto.requests.CreateSessionRequest
import edu.itmo.ultimatumgame.dto.responses.MyRole
import edu.itmo.ultimatumgame.dto.responses.PendingActionType
import edu.itmo.ultimatumgame.dto.responses.RoundResponse
import edu.itmo.ultimatumgame.dto.responses.SessionResponse
import edu.itmo.ultimatumgame.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatumgame.exceptions.IdNotFoundException
import edu.itmo.ultimatumgame.exceptions.SessionJoinRejectedException
import edu.itmo.ultimatumgame.model.NpcParams
import edu.itmo.ultimatumgame.model.NpcProfile
import edu.itmo.ultimatumgame.model.NpcStrategy
import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.model.Round
import edu.itmo.ultimatumgame.model.RoundPhase
import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.model.SessionState
import edu.itmo.ultimatumgame.model.SessionType
import edu.itmo.ultimatumgame.model.User
import edu.itmo.ultimatumgame.repositories.RoundRepository
import edu.itmo.ultimatumgame.repositories.SessionRepository
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.RoundMapper
import edu.itmo.ultimatumgame.util.SessionMapper
import edu.itmo.ultimatumgame.util.SessionWithTeamsAndMembersMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionServiceTest {

    private val sessionRepo = mockk<SessionRepository>()
    private val roundRepo = mockk<RoundRepository>()
    private val sessionMapper = mockk<SessionMapper>()
    private val sessionWithTeamsAndMembersMapper = mockk<SessionWithTeamsAndMembersMapper>()
    private val roundMapper = mockk<RoundMapper>()
    private val userService = mockk<UserService>()
    private val securityService = mockk<SecurityService>()
    private val eventPublisher = mockk<EventPublisherService>(relaxUnitFun = true)
    private val domainEventLogger = mockk<DomainEventLogger>(relaxUnitFun = true)
    private val npcProfileRepository = mockk<edu.itmo.ultimatumgame.repositories.NpcProfileRepository>(relaxed = true)
    private val userRepository = mockk<edu.itmo.ultimatumgame.repositories.UserRepository>(relaxed = true)
    private val service = SessionService(
        sessionRepo,
        roundRepo,
        sessionMapper,
        sessionWithTeamsAndMembersMapper,
        roundMapper,
        userService,
        securityService,
        eventPublisher,
        domainEventLogger,
        npcProfileRepository,
        userRepository,
    )

    // ---------- createSession ----------

    @Test
    fun `createSession — FREE_FOR_ALL создаёт rounds по numRounds, teams=0`() {
        val req = CreateSessionRequest()
        val cfg = sessionConfig(sessionType = SessionType.FREE_FOR_ALL, numRounds = 3, numTeams = 0)
        val newSession = Session(
            id = UUID.randomUUID(),
            displayName = "s",
            state = SessionState.CREATED,
            config = cfg,
        )
        val currentUser = user(role = Role.ADMIN)
        val dto = mockk<SessionWithTeamsAndMembersResponse>(relaxed = true)

        every { sessionMapper.toEntity(req) } returns newSession
        every { userService.getCurrentUser() } returns currentUser
        val savedSlot = slot<Session>()
        every { sessionRepo.save(capture(savedSlot)) } answers { firstArg() }
        every { sessionWithTeamsAndMembersMapper.toDto(any()) } returns dto

        val result = service.createSession(req)

        assertSame(dto, result)
        assertEquals(3, savedSlot.captured.rounds.size)
        assertEquals(currentUser, savedSlot.captured.admin)
        assertTrue(savedSlot.captured.teams.isEmpty(), "FREE_FOR_ALL — teams не создаются")
    }

    @Test
    fun `createSession — сохраняет autoAdvanceRounds=true`() {
        val req = CreateSessionRequest()
        val cfg = sessionConfig(autoAdvanceRounds = true)
        val newSession = Session(
            id = UUID.randomUUID(),
            displayName = "s",
            state = SessionState.CREATED,
            config = cfg,
        )
        every { sessionMapper.toEntity(req) } returns newSession
        every { userService.getCurrentUser() } returns user(role = Role.ADMIN)
        val savedSlot = slot<Session>()
        every { sessionRepo.save(capture(savedSlot)) } answers { firstArg() }
        every { sessionWithTeamsAndMembersMapper.toDto(any()) } returns mockk(relaxed = true)

        service.createSession(req)

        assertEquals(true, savedSlot.captured.config!!.autoAdvanceRounds)
    }

    @Test
    fun `createSession — TEAM_BATTLE создаёт numTeams команд`() {
        val req = CreateSessionRequest()
        val cfg = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numRounds = 2, numTeams = 3)
        val newSession = Session(
            id = UUID.randomUUID(),
            displayName = "s",
            state = SessionState.CREATED,
            config = cfg,
        )
        every { sessionMapper.toEntity(req) } returns newSession
        every { userService.getCurrentUser() } returns user(role = Role.ADMIN)
        every { sessionRepo.save(any<Session>()) } answers { firstArg() }
        every { sessionWithTeamsAndMembersMapper.toDto(any()) } returns mockk(relaxed = true)

        service.createSession(req)

        assertEquals(3, newSession.teams.size)
    }

    @Test
    fun `createSession TEAM_BATTLE с numTeams меньше 2 — IllegalArgumentException`() {
        val cfg = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numTeams = 1)
        val newSession = Session(id = UUID.randomUUID(), displayName = "s", state = SessionState.CREATED, config = cfg)
        every { sessionMapper.toEntity(any()) } returns newSession
        every { userService.getCurrentUser() } returns user()

        assertThrows<IllegalArgumentException> { service.createSession(CreateSessionRequest()) }
    }

    @Test
    fun `createSession FREE_FOR_ALL с numTeams больше 0 — IllegalArgumentException`() {
        val cfg = sessionConfig(sessionType = SessionType.FREE_FOR_ALL, numTeams = 2)
        val newSession = Session(id = UUID.randomUUID(), displayName = "s", state = SessionState.CREATED, config = cfg)
        every { sessionMapper.toEntity(any()) } returns newSession
        every { userService.getCurrentUser() } returns user()

        assertThrows<IllegalArgumentException> { service.createSession(CreateSessionRequest()) }
    }

    // ---------- setters / getters ----------

    @Test
    fun `setSessionState — обновляет state и сохраняет`() {
        val s = session(state = SessionState.CREATED)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s

        service.setSessionState(s.id!!, SessionState.RUNNING)

        assertEquals(SessionState.RUNNING, s.state)
    }

    @Test
    fun `setCurrentRound — проставляет currentRound и сохраняет`() {
        val s = session()
        val r = Round(id = UUID.randomUUID(), session = s, roundNumber = 1)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s

        service.setCurrentRound(s.id!!, r)

        assertSame(r, s.currentRound)
    }

    @Test
    fun `getSession — NotFound если сессии нет`() {
        val id = UUID.randomUUID()
        every { sessionRepo.findById(id) } returns Optional.empty()
        assertThrows<IdNotFoundException> { service.getSession(id) }
    }

    @Test
    fun `getSession — возвращает DTO`() {
        val s = session()
        val dto = mockk<SessionResponse>()
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionMapper.toDto(s) } returns dto

        assertSame(dto, service.getSession(s.id!!))
    }

    @Test
    fun `getSessionEntity — возвращает entity`() {
        val s = session()
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        assertSame(s, service.getSessionEntity(s.id!!))
    }

    @Test
    fun `getSessionWithTeamsAndMembers — NotFound если сессии нет`() {
        val id = UUID.randomUUID()
        every { sessionRepo.findById(id) } returns Optional.empty()
        assertThrows<IdNotFoundException> { service.getSessionWithTeamsAndMembers(id) }
    }

    @Test
    fun `getSessionWithTeamsAndMembers — возвращает DTO`() {
        val s = session()
        val dto = mockk<SessionWithTeamsAndMembersResponse>()
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns dto

        assertSame(dto, service.getSessionWithTeamsAndMembers(s.id!!))
    }

    @Test
    fun `getCurrentRound — возвращает DTO раунда`() {
        val s = session()
        val r = Round(id = UUID.randomUUID(), session = s, roundNumber = 1)
        s.currentRound = r
        val dto = mockk<RoundResponse>()
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { roundMapper.toDto(r) } returns dto
        // no user context — enrich returns dto as-is
        every { securityService.getCurrentUserIdOrNull() } returns null

        assertSame(dto, service.getCurrentRound(s.id!!))
    }

    @Test
    fun `getCurrentRound — NotFound если currentRound null`() {
        val s = session(currentRound = null)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        assertThrows<IdNotFoundException> { service.getCurrentRound(s.id!!) }
    }

    @Test
    fun `getRounds — возвращает список DTO отсортированный по roundNumber`() {
        val s = session()
        val r1 = Round(id = UUID.randomUUID(), session = s, roundNumber = 1)
        val r2 = Round(id = UUID.randomUUID(), session = s, roundNumber = 2)
        val r3 = Round(id = UUID.randomUUID(), session = s, roundNumber = 3)
        val d1 = mockk<RoundResponse>()
        every { d1.roundNumber } returns 1
        val d2 = mockk<RoundResponse>()
        every { d2.roundNumber } returns 2
        val d3 = mockk<RoundResponse>()
        every { d3.roundNumber } returns 3
        every { sessionRepo.existsById(s.id!!) } returns true
        // repository возвращает произвольный порядок — сервис должен сортировать по roundNumber
        every { roundRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(r3, r1, r2)
        every { roundMapper.toDto(r1) } returns d1
        every { roundMapper.toDto(r2) } returns d2
        every { roundMapper.toDto(r3) } returns d3
        // no user context — enrich returns dto as-is
        every { securityService.getCurrentUserIdOrNull() } returns null

        val result = service.getRounds(s.id!!)

        assertEquals(listOf(1, 2, 3), result.map { it.roundNumber })
    }

    @Test
    fun `getRounds — NotFound если сессии нет`() {
        val id = UUID.randomUUID()
        every { sessionRepo.existsById(id) } returns false
        assertThrows<IdNotFoundException> { service.getRounds(id) }
    }

    @Test
    fun `getRounds — myRole вычисляется из offers relative to currentUser`() {
        val me = user()
        val other = user()
        val s = session(members = mutableSetOf(me, other))
        val r = TestFixtures.round(session = s, roundNumber = 1)
        val offFromMe = offer(proposer = me, responder = other, round = r)
        val offToMe = offer(proposer = other, responder = me, round = r)
        r.offers = mutableListOf(offFromMe, offToMe)
        r.roundPhase = RoundPhase.OFFERS_SENT

        val baseDto = RoundResponse(
            id = r.id!!,
            roundNumber = 1,
            roundPhase = r.roundPhase!!,
            offers = mutableListOf(),
            decisions = mutableListOf(),
            session = mockk(relaxed = true),
        )
        every { sessionRepo.existsById(s.id!!) } returns true
        every { roundRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(r)
        every { roundMapper.toDto(r) } returns baseDto
        every { securityService.getCurrentUserIdOrNull() } returns me.id!!

        val result = service.getRounds(s.id!!)

        assertEquals(MyRole.BOTH, result[0].myRole)
        val actions = result[0].myPendingActions
        assertEquals(1, actions.size)
        assertEquals(PendingActionType.MAKE_DECISION, actions[0].type)
        assertEquals(offToMe.id, actions[0].offerId)
    }

    @Test
    fun `getRounds — WAIT_OFFERS + user не отправил offer → SEND_OFFER pending`() {
        val me = user()
        val s = session(members = mutableSetOf(me))
        val r = TestFixtures.round(session = s, roundNumber = 1)
        r.roundPhase = RoundPhase.WAIT_OFFERS
        val baseDto = RoundResponse(
            id = r.id!!,
            roundNumber = 1,
            roundPhase = r.roundPhase!!,
            offers = mutableListOf(),
            decisions = mutableListOf(),
            session = mockk(relaxed = true),
        )
        every { sessionRepo.existsById(s.id!!) } returns true
        every { roundRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(r)
        every { roundMapper.toDto(r) } returns baseDto
        every { securityService.getCurrentUserIdOrNull() } returns me.id!!

        val result = service.getRounds(s.id!!)

        assertEquals(MyRole.NONE, result[0].myRole)
        assertEquals(1, result[0].myPendingActions.size)
        assertEquals(PendingActionType.SEND_OFFER, result[0].myPendingActions[0].type)
    }

    @Test
    fun `getRounds — observer не участник → myRole=NONE, actions пусто`() {
        val observer = user()
        val a = user()
        val b = user()
        val s = session(members = mutableSetOf(a, b))
        val r = TestFixtures.round(session = s, roundNumber = 1)
        r.roundPhase = RoundPhase.OFFERS_SENT
        r.offers = mutableListOf(offer(proposer = a, responder = b, round = r))
        val baseDto = RoundResponse(
            id = r.id!!,
            roundNumber = 1,
            roundPhase = r.roundPhase!!,
            offers = mutableListOf(),
            decisions = mutableListOf(),
            session = mockk(relaxed = true),
        )
        every { sessionRepo.existsById(s.id!!) } returns true
        every { roundRepo.findAllBySessionIdWithRelations(s.id!!) } returns listOf(r)
        every { roundMapper.toDto(r) } returns baseDto
        every { securityService.getCurrentUserIdOrNull() } returns observer.id!!

        val result = service.getRounds(s.id!!)

        assertEquals(MyRole.NONE, result[0].myRole)
        assertTrue(result[0].myPendingActions.isEmpty())
    }

    @Test
    fun `getAllSessions — пустая строка поиска = findAll`() {
        val s = session()
        val page: Page<Session> = PageImpl(listOf(s))
        val dto = mockk<SessionResponse>()
        every { sessionRepo.findAll(any<Pageable>()) } returns page
        every { sessionMapper.toDto(s) } returns dto

        val result = service.getAllSessions(0, 10, "")
        assertEquals(1, result.content.size)
        verify(exactly = 0) { sessionRepo.searchByNameTrgm(any(), any(), any()) }
    }

    @Test
    fun `getAllSessions — непустая строка = searchByNameTrgm`() {
        val s = session()
        val page: Page<Session> = PageImpl(listOf(s))
        every { sessionRepo.searchByNameTrgm("foo", "%foo%", any()) } returns page
        every { sessionMapper.toDto(s) } returns mockk()

        val result = service.getAllSessions(0, 10, "foo")
        assertEquals(1, result.content.size)
        verify { sessionRepo.searchByNameTrgm("foo", "%foo%", any()) }
    }

    // ---------- joinSession ----------

    @Test
    fun `joinSession FREE_FOR_ALL — добавляет user в members и публикует статус`() {
        val u = user()
        val s = session(members = mutableSetOf(), config = sessionConfig(numPlayers = 4))
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)

        service.joinSession(s.id!!, null)

        assertTrue(s.members.contains(u))
        verify { eventPublisher.publishSessionStatus(s.id!!, s) }
    }

    @Test
    fun `joinSession — если уже в members, возвращает DTO без изменений`() {
        val u = user()
        val s = session(members = mutableSetOf(u))
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)

        service.joinSession(s.id!!, null)

        verify(exactly = 0) { sessionRepo.save(any()) }
        verify(exactly = 0) { eventPublisher.publishSessionStatus(any(), any()) }
    }

    @Test
    fun `joinSession — SessionJoinRejectedException если сессия закрыта`() {
        val u = user()
        val s = session(openToConnect = false, members = mutableSetOf())
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)

        assertThrows<SessionJoinRejectedException> { service.joinSession(s.id!!, null) }
    }

    @Test
    fun `joinSession — SessionJoinRejectedException при переполнении`() {
        val u = user()
        val filler = user()
        val s = session(
            members = mutableSetOf(filler),
            config = sessionConfig(numPlayers = 1),
        )
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)

        assertThrows<SessionJoinRejectedException> { service.joinSession(s.id!!, null) }
    }

    @Test
    fun `joinSession — SessionJoinRejectedException если admin пытается joinнуться`() {
        val admin = user(role = Role.ADMIN)
        val s = session(admin = admin, members = mutableSetOf())
        every { userService.getCurrentUser() } returns admin
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)

        assertThrows<SessionJoinRejectedException> { service.joinSession(s.id!!, null) }
    }

    @Test
    fun `joinSession TEAM_BATTLE — добавляет user и в session_members, и в team_members`() {
        val u = user()
        val teamId = UUID.randomUUID()
        val tA = team(id = teamId, members = mutableSetOf())
        val cfg = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numTeams = 2, numPlayers = 4)
        val s = session(members = mutableSetOf(), teams = mutableSetOf(tA), config = cfg)
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)

        service.joinSession(s.id!!, teamId)

        assertTrue(s.members.contains(u))
        assertTrue(tA.members.contains(u))
    }

    @Test
    fun `joinSession TEAM_BATTLE — error если teamId null`() {
        val u = user()
        val cfg = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numTeams = 2)
        val s = session(members = mutableSetOf(), config = cfg)
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)

        assertThrows<IllegalStateException> { service.joinSession(s.id!!, null) }
    }

    @Test
    fun `joinSession TEAM_BATTLE — error если teamId не найден в session_teams`() {
        val u = user()
        val cfg = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numTeams = 2)
        val s = session(members = mutableSetOf(), teams = mutableSetOf(team()), config = cfg)
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)

        assertThrows<IllegalStateException> { service.joinSession(s.id!!, UUID.randomUUID()) }
    }

    // ---------- joinSessionAsObserver ----------

    @Test
    fun `joinSessionAsObserver — добавляет user в observers`() {
        val u = user()
        val s = session(members = mutableSetOf(), observers = mutableSetOf())
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)

        service.joinSessionAsObserver(s.id!!)

        assertTrue(s.observers.contains(u))
    }

    @Test
    fun `joinSessionAsObserver — если уже в observers, возвращает DTO без изменений`() {
        val u = user()
        val s = session(observers = mutableSetOf(u))
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)

        service.joinSessionAsObserver(s.id!!)

        verify(exactly = 0) { sessionRepo.save(any()) }
    }

    @Test
    fun `joinSessionAsObserver — SessionJoinRejectedException если сессия закрыта`() {
        val u = user()
        val s = session(openToConnect = false, observers = mutableSetOf())
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)

        assertThrows<SessionJoinRejectedException> { service.joinSessionAsObserver(s.id!!) }
    }

    @Test
    fun `joinSessionAsObserver — admin не может стать observer`() {
        val admin = user(role = Role.ADMIN)
        val s = session(admin = admin, observers = mutableSetOf())
        every { userService.getCurrentUser() } returns admin
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)

        assertThrows<SessionJoinRejectedException> { service.joinSessionAsObserver(s.id!!) }
    }

    // ---------- auto-close полной сессии (T-093) ----------

    @Test
    fun `joinSession — при достижении numPlayers сессия авто-закрывается (openToConnect=false)`() {
        val newUser = user()
        val filler = user()
        val s = session(
            members = mutableSetOf(filler),
            config = sessionConfig(sessionType = SessionType.FREE_FOR_ALL, numPlayers = 2),
        )
        every { userService.getCurrentUser() } returns newUser
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)

        assertTrue(s.openToConnect, "prerequisite: сессия ещё открыта")
        service.joinSession(s.id!!, null)

        assertFalse(s.openToConnect, "после заполнения openToConnect должен стать false")
    }

    @Test
    fun `joinSession — при members меньше numPlayers openToConnect остаётся true`() {
        val newUser = user()
        val s = session(
            members = mutableSetOf(),
            config = sessionConfig(sessionType = SessionType.FREE_FOR_ALL, numPlayers = 3),
        )
        every { userService.getCurrentUser() } returns newUser
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)

        service.joinSession(s.id!!, null)

        assertTrue(s.openToConnect, "1 из 3 — сессия должна остаться открытой")
    }

    @Test
    fun `addNpcMember — авто-закрытие при заполнении`() {
        val filler = user()
        val npcUser = user(role = Role.NPC)
        val profileId = UUID.randomUUID()
        val profile = edu.itmo.ultimatumgame.model.NpcProfile(
            id = profileId,
            user = npcUser,
            strategy = edu.itmo.ultimatumgame.model.NpcStrategy.FAIR,
            params = edu.itmo.ultimatumgame.model.NpcParams.Fair(),
        )
        val s = session(
            members = mutableSetOf(filler),
            config = sessionConfig(sessionType = SessionType.FREE_FOR_ALL, numPlayers = 2),
        )
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)
        every { npcProfileRepository.findById(profileId) } returns Optional.of(profile)

        service.addNpcMember(s.id!!, profileId, null)

        assertFalse(s.openToConnect, "второй NPC должен закрыть сессию")
    }

    @Test
    fun `bulkCreateAndJoinNpcs — bulk заполняющий до numPlayers авто-закрывает`() {
        val filler = user()
        val cfg = sessionConfig(sessionType = SessionType.FREE_FOR_ALL, numPlayers = 3, numTeams = 0)
        val s = session(members = mutableSetOf(filler), teams = mutableSetOf(), config = cfg)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)
        stubNpcSaves()

        val req = BulkNpcsRequest(count = 2, strategy = NpcStrategy.FAIR, params = NpcParams.Fair())
        service.bulkCreateAndJoinNpcs(s.id!!, req)

        assertFalse(s.openToConnect, "1 human + 2 NPC = 3/3, сессия должна закрыться")
    }

    // ---------- bulkCreateAndJoinNpcs ----------

    private fun stubNpcSaves() {
        every { userRepository.save(any<User>()) } answers {
            firstArg<User>().copy(id = UUID.randomUUID())
        }
        every { npcProfileRepository.save(any<NpcProfile>()) } answers {
            firstArg<NpcProfile>().apply { id = UUID.randomUUID() }
        }
    }

    @Test
    fun `bulkCreateAndJoinNpcs FREE_FOR_ALL — все NPC в session_members, teams не трогаем`() {
        val s = session(
            members = mutableSetOf(),
            teams = mutableSetOf(),
            config = sessionConfig(sessionType = SessionType.FREE_FOR_ALL, numTeams = 0, numPlayers = 10),
        )
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)
        stubNpcSaves()

        val req = BulkNpcsRequest(count = 3, strategy = NpcStrategy.FAIR, params = NpcParams.Fair())
        service.bulkCreateAndJoinNpcs(s.id!!, req)

        assertEquals(3, s.members.size)
        assertTrue(s.teams.isEmpty())
    }

    @Test
    fun `bulkCreateAndJoinNpcs TEAM_BATTLE teamId=null — round-robin по пустым командам`() {
        val tA = team(members = mutableSetOf())
        val tB = team(members = mutableSetOf())
        val cfg = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numTeams = 2, numPlayers = 10)
        val s = session(members = mutableSetOf(), teams = mutableSetOf(tA, tB), config = cfg)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)
        stubNpcSaves()

        val req = BulkNpcsRequest(count = 4, strategy = NpcStrategy.FAIR, params = NpcParams.Fair())
        service.bulkCreateAndJoinNpcs(s.id!!, req)

        assertEquals(4, s.members.size)
        assertEquals(2, tA.members.size)
        assertEquals(2, tB.members.size)
    }

    @Test
    fun `bulkCreateAndJoinNpcs TEAM_BATTLE teamId=null — least-full first (2 - 0 → +0 - +3)`() {
        val filler1 = user()
        val filler2 = user()
        val tA = team(members = mutableSetOf(filler1, filler2))
        val tB = team(members = mutableSetOf())
        val cfg = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numTeams = 2, numPlayers = 10)
        val s = session(
            members = mutableSetOf(filler1, filler2),
            teams = mutableSetOf(tA, tB),
            config = cfg,
        )
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)
        stubNpcSaves()

        val req = BulkNpcsRequest(count = 3, strategy = NpcStrategy.FAIR, params = NpcParams.Fair())
        service.bulkCreateAndJoinNpcs(s.id!!, req)

        // tA уже 2, tB — 0. Три новых NPC должны сначала выравнять (tB +2), затем ещё один либо в A либо в B
        // (оба по 2). Проверяем инвариант: разница не больше 1, суммарно 5 в командах, все 3 новых — в session.members.
        assertEquals(5, s.members.size)
        assertEquals(5, tA.members.size + tB.members.size)
        assertTrue(kotlin.math.abs(tA.members.size - tB.members.size) <= 1)
        // Конкретно: tB получает первые двух, третий — в любую (наши 3 новых)
        assertEquals(3, (tA.members.size - 2) + tB.members.size)
    }

    @Test
    fun `bulkCreateAndJoinNpcs TEAM_BATTLE teamId указан — все N в указанную команду`() {
        val tA = team(members = mutableSetOf())
        val tB = team(members = mutableSetOf())
        val cfg = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numTeams = 2, numPlayers = 10)
        val s = session(members = mutableSetOf(), teams = mutableSetOf(tA, tB), config = cfg)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)
        stubNpcSaves()

        val req = BulkNpcsRequest(
            count = 5,
            strategy = NpcStrategy.FAIR,
            params = NpcParams.Fair(),
            teamId = tA.id,
        )
        service.bulkCreateAndJoinNpcs(s.id!!, req)

        assertEquals(5, s.members.size)
        assertEquals(5, tA.members.size)
        assertTrue(tB.members.isEmpty())
    }

    @Test
    fun `bulkCreateAndJoinNpcs TEAM_BATTLE teamId неизвестен — IdNotFoundException`() {
        val tA = team(members = mutableSetOf())
        val cfg = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numTeams = 2, numPlayers = 10)
        val s = session(members = mutableSetOf(), teams = mutableSetOf(tA), config = cfg)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        stubNpcSaves()

        val req = BulkNpcsRequest(
            count = 2,
            strategy = NpcStrategy.FAIR,
            params = NpcParams.Fair(),
            teamId = UUID.randomUUID(),
        )
        assertThrows<IdNotFoundException> { service.bulkCreateAndJoinNpcs(s.id!!, req) }
    }

    @Test
    fun `bulkCreateAndJoinNpcs FREE_FOR_ALL teamId указан — IllegalArgumentException`() {
        val cfg = sessionConfig(sessionType = SessionType.FREE_FOR_ALL, numTeams = 0, numPlayers = 10)
        val s = session(members = mutableSetOf(), teams = mutableSetOf(), config = cfg)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        stubNpcSaves()

        val req = BulkNpcsRequest(
            count = 2,
            strategy = NpcStrategy.FAIR,
            params = NpcParams.Fair(),
            teamId = UUID.randomUUID(),
        )
        assertThrows<IllegalArgumentException> { service.bulkCreateAndJoinNpcs(s.id!!, req) }
    }

    // ---------- joinSessionAsObserver (continued) ----------

    @Test
    fun `joinSessionAsObserver — если user был member, убирает его из members и team_members`() {
        val u = user()
        val tA = team(members = mutableSetOf(u))
        val s = session(members = mutableSetOf(u), teams = mutableSetOf(tA), observers = mutableSetOf())
        every { userService.getCurrentUser() } returns u
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        every { sessionRepo.save(s) } returns s
        every { sessionWithTeamsAndMembersMapper.toDto(s) } returns mockk(relaxed = true)

        service.joinSessionAsObserver(s.id!!)

        assertFalse(s.members.contains(u))
        assertFalse(tA.members.contains(u))
        assertTrue(s.observers.contains(u))
    }
}
