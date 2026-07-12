package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.TestFixtures.session
import edu.itmo.ultimatum_game.TestFixtures.sessionConfig
import edu.itmo.ultimatum_game.TestFixtures.team
import edu.itmo.ultimatum_game.TestFixtures.user
import edu.itmo.ultimatum_game.dto.requests.CreateSessionRequest
import edu.itmo.ultimatum_game.dto.responses.RoundResponse
import edu.itmo.ultimatum_game.dto.responses.SessionResponse
import edu.itmo.ultimatum_game.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatum_game.exceptions.IdNotFoundException
import edu.itmo.ultimatum_game.exceptions.SessionJoinRejectedException
import edu.itmo.ultimatum_game.model.Role
import edu.itmo.ultimatum_game.model.Round
import edu.itmo.ultimatum_game.model.Session
import edu.itmo.ultimatum_game.model.SessionState
import edu.itmo.ultimatum_game.model.SessionType
import edu.itmo.ultimatum_game.model.Team
import edu.itmo.ultimatum_game.model.User
import edu.itmo.ultimatum_game.repositories.SessionRepository
import edu.itmo.ultimatum_game.util.RoundMapper
import edu.itmo.ultimatum_game.util.SessionMapper
import edu.itmo.ultimatum_game.util.SessionWithTeamsAndMembersMapper
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertSame

class SessionServiceTest {

    private val sessionRepo = mockk<SessionRepository>()
    private val sessionMapper = mockk<SessionMapper>()
    private val sessionWithTeamsAndMembersMapper = mockk<SessionWithTeamsAndMembersMapper>()
    private val roundMapper = mockk<RoundMapper>()
    private val userService = mockk<UserService>()
    private val eventPublisher = mockk<EventPublisherService>(relaxUnitFun = true)
    private val service = SessionService(
        sessionRepo, sessionMapper, sessionWithTeamsAndMembersMapper, roundMapper, userService, eventPublisher,
    )

    // ---------- createSession ----------

    @Test
    fun `createSession — FREE_FOR_ALL создаёт rounds по numRounds, teams=0`() {
        val req = CreateSessionRequest()
        val cfg = sessionConfig(sessionType = SessionType.FREE_FOR_ALL, numRounds = 3, numTeams = 0)
        val newSession = Session(
            id = UUID.randomUUID(),
            displayName = "s", state = SessionState.CREATED,
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
    fun `createSession — TEAM_BATTLE создаёт numTeams команд`() {
        val req = CreateSessionRequest()
        val cfg = sessionConfig(sessionType = SessionType.TEAM_BATTLE, numRounds = 2, numTeams = 3)
        val newSession = Session(
            id = UUID.randomUUID(), displayName = "s", state = SessionState.CREATED, config = cfg,
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

        assertSame(dto, service.getCurrentRound(s.id!!))
    }

    @Test
    fun `getCurrentRound — NotFound если currentRound null`() {
        val s = session(currentRound = null)
        every { sessionRepo.findById(s.id!!) } returns Optional.of(s)
        assertThrows<IdNotFoundException> { service.getCurrentRound(s.id!!) }
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
