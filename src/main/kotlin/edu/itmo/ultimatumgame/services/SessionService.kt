@file:Suppress(
    "MaxLineLength",
    "MaximumLineLength",
    "TooManyFunctions",
    "ThrowsCount",
    "LongParameterList",
    "UnsafeCallOnNullableType",
)

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.requests.CreateSessionRequest
import edu.itmo.ultimatumgame.dto.responses.MyRole
import edu.itmo.ultimatumgame.dto.responses.PendingAction
import edu.itmo.ultimatumgame.dto.responses.PendingActionType
import edu.itmo.ultimatumgame.dto.responses.RoundResponse
import edu.itmo.ultimatumgame.dto.responses.SessionResponse
import edu.itmo.ultimatumgame.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatumgame.exceptions.IdNotFoundException
import edu.itmo.ultimatumgame.exceptions.SessionJoinRejectedException
import edu.itmo.ultimatumgame.model.Round
import edu.itmo.ultimatumgame.model.RoundPhase
import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.model.SessionState
import edu.itmo.ultimatumgame.model.SessionType
import edu.itmo.ultimatumgame.model.Team
import edu.itmo.ultimatumgame.model.User
import edu.itmo.ultimatumgame.repositories.RoundRepository
import edu.itmo.ultimatumgame.repositories.SessionRepository
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.PlayerJoined
import edu.itmo.ultimatumgame.util.RoundMapper
import edu.itmo.ultimatumgame.util.SessionCreated
import edu.itmo.ultimatumgame.util.SessionMapper
import edu.itmo.ultimatumgame.util.SessionWithTeamsAndMembersMapper
import edu.itmo.ultimatumgame.util.logger
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SessionService(
    private val sessionRepository: SessionRepository,
    private val roundRepository: RoundRepository,
    private val sessionMapper: SessionMapper,
    private val sessionWithTeamsAndMembersMapper: SessionWithTeamsAndMembersMapper,
    private val roundMapper: RoundMapper,
    private val userService: UserService,
    private val securityService: SecurityService,
    private val eventPublisherService: EventPublisherService,
    private val domainEventLogger: DomainEventLogger,
) {

    private val logger = logger()

    @Transactional
    fun createSession(createSessionRequest: CreateSessionRequest): SessionWithTeamsAndMembersResponse {
        logger.info("Создание новой сессии из запроса {}", createSessionRequest)
        var newSession = sessionMapper.toEntity(createSessionRequest)
        logger.debug("Новая сессия после маппинга {}", newSession)
        newSession.admin = userService.getCurrentUser()
        logger.debug("Новая сессия после добавления админа {}", newSession)
        createRounds(newSession)
        logger.debug("Новая сессия после initRounds {}", newSession)
        createTeams(newSession)
        logger.debug("Новая сессия после initTeams {}", newSession)
        newSession = sessionRepository.save(newSession)
        logger.debug("Новая сессия после save {}", newSession)
        domainEventLogger.emit(
            SessionCreated(
                sessionId = newSession.id!!,
                adminId = newSession.admin!!.id!!,
                sessionType = newSession.config!!.sessionType,
            )
        )
        val dto = sessionWithTeamsAndMembersMapper.toDto(newSession)
        return dto
    }

    @Transactional
    fun setSessionState(sessionId: UUID, newSessionState: SessionState) {
        val session = getSessionEntity(sessionId)
        session.state = newSessionState
        sessionRepository.save(session)
    }

    @Transactional
    fun setCurrentRound(sessionId: UUID, newRound: Round) {
        val session = getSessionEntity(sessionId)
        session.currentRound = newRound
        sessionRepository.save(session)
    }

    fun getSession(sessionId: UUID): SessionResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { IdNotFoundException("Сессия с $sessionId не найдена") }
        logger.debug("Найдена сессия {}", session)
        val dto = sessionMapper.toDto(session)
        return dto
    }

    fun getSessionEntity(sessionId: UUID): Session {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { IdNotFoundException("Сессия с $sessionId не найдена") }
        return session
    }

    fun getSessionWithTeamsAndMembers(sessionId: UUID): SessionWithTeamsAndMembersResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { IdNotFoundException("Сессия с $sessionId не найдена") }
        logger.debug("Найдена сессия {}", session)
        val dto = sessionWithTeamsAndMembersMapper.toDto(session)
        return dto
    }

    fun getCurrentRound(sessionId: UUID): RoundResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { IdNotFoundException("Сессия с $sessionId не найдена") }
        logger.debug("Найдена сессия {}", session)
        val round = session.currentRound ?: throw IdNotFoundException("Текущий раунд null сессия ${session.displayName} еще не началась")
        val dto = roundMapper.toDto(round)
        return enrichWithHints(dto, round, session.members)
    }

    fun getRounds(sessionId: UUID): List<RoundResponse> {
        if (!sessionRepository.existsById(sessionId)) {
            throw IdNotFoundException("Сессия с $sessionId не найдена")
        }
        val rounds = roundRepository.findAllBySessionIdWithRelations(sessionId)
        logger.debug("getRounds — найдена сессия {}, раундов: {}", sessionId, rounds.size)
        return rounds
            .sortedBy { it.roundNumber }
            .map { round ->
                val members = round.session?.members.orEmpty()
                enrichWithHints(roundMapper.toDto(round), round, members)
            }
    }

    private fun enrichWithHints(
        dto: RoundResponse,
        round: Round,
        members: Set<User>,
    ): RoundResponse {
        val userId = runCatching { securityService.getCurrentUserId() }.getOrNull() ?: return dto
        val role = computeMyRole(round, userId)
        val actions = computeMyPendingActions(round, userId, members)
        return dto.copy(myRole = role, myPendingActions = actions)
    }

    private fun computeMyRole(round: Round, userId: UUID): MyRole {
        val asProposer = round.offers.any { it.proposer?.id == userId }
        val asResponder = round.offers.any { it.responder?.id == userId }
        return when {
            asProposer && asResponder -> MyRole.BOTH
            asProposer -> MyRole.PROPOSER
            asResponder -> MyRole.RESPONDER
            else -> MyRole.NONE
        }
    }

    private fun computeMyPendingActions(
        round: Round,
        userId: UUID,
        members: Set<User>,
    ): List<PendingAction> {
        val isMember = members.any { it.id == userId }
        if (!isMember) return emptyList()

        return when (round.roundPhase) {
            RoundPhase.WAIT_OFFERS -> {
                if (round.offers.none { it.proposer?.id == userId }) {
                    listOf(PendingAction(type = PendingActionType.SEND_OFFER))
                } else {
                    emptyList()
                }
            }
            RoundPhase.OFFERS_SENT -> {
                round.offers
                    .filter { it.responder?.id == userId }
                    .filter { offer -> round.decisions.none { it.offer?.id == offer.id } }
                    .map { offer ->
                        PendingAction(
                            type = PendingActionType.MAKE_DECISION,
                            offerId = offer.id,
                        )
                    }
            }
            else -> emptyList()
        }
    }

    fun getAllSessions(page: Int, pageSize: Int, s: String): Page<SessionResponse> {
//        val pageable = createPageable(page, pageSize)
        val sessions: Page<Session> =
            if (s.isBlank()) {
                sessionRepository.findAll(
                    PageRequest.of(
                        page,
                        pageSize,
                        Sort.by("createdAt").descending()
                    )
                )
            } else {
                sessionRepository.searchByNameTrgm(
                    s,
                    "%$s%",
                    PageRequest.of(page, pageSize, Sort.by("created_at").descending())
                )
            }
        val response = sessions.map { sessionMapper.toDto(it) }
        logger.info("По запросу '$s' найдено ${response.size} сессий")
        return response
    }

    @Transactional
    fun joinSession(sessionId: UUID, teamId: UUID?): SessionWithTeamsAndMembersResponse {
        val user = userService.getCurrentUser()
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { IdNotFoundException("Сессия с $sessionId не найдена") }
        val config = session.config
            ?: error("Session.config не должен быть null к этому моменту")
        if (session.members.contains(user)) {
            return sessionWithTeamsAndMembersMapper.toDto(session)
        }
        if (!session.openToConnect) throw SessionJoinRejectedException("Сессия $sessionId закрыта для подключений")
        if (session.members.size >= config.numPlayers) {
            throw SessionJoinRejectedException(
                "В сессии $sessionId достигнуто максимальное количество игроков (${session.members.size} из ${config.numPlayers})"
            )
        }
        if (session.admin == user) {
            throw SessionJoinRejectedException(
                "Вы на можете подключиться к сессии в качестве участника, так как вы ее администратор"
            )
        }
        if (config.sessionType == SessionType.TEAM_BATTLE) {
            val tId = teamId
                ?: error("Для подключения к режиму ${config.sessionType} обязательно надо указывать teamId")
            val team: Team = session.teams.find { it.id == tId }
                ?: error("В сессии $sessionId не найдена команда $tId")
            team.members += user
            session.members += user
            session.observers.remove(user)
            sessionRepository.save(session)
        } else {
            session.members += user
            session.observers.remove(user)
            sessionRepository.save(session)
        }
        eventPublisherService.publishSessionStatus(sessionId, session)
        domainEventLogger.emit(PlayerJoined(sessionId = sessionId, userId = user.id!!, role = user.role))
        val dto = sessionWithTeamsAndMembersMapper.toDto(session)
        return dto
    }

    @Transactional
    fun joinSessionAsObserver(sessionId: UUID): SessionWithTeamsAndMembersResponse {
        val observer = userService.getCurrentUser()
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { IdNotFoundException("Сессия с $sessionId не найдена") }
        if (session.observers.contains(observer)) return sessionWithTeamsAndMembersMapper.toDto(session)
        if (!session.openToConnect) throw SessionJoinRejectedException("Сессия $sessionId закрыта для подключений")
        if (session.admin == observer) {
            throw SessionJoinRejectedException(
                "Вы на можете подключиться к сессии в качестве зрителя, так как вы ее администратор"
            )
        }
        session.observers += observer
        if (session.members.remove(observer)) {
            session.teams.forEach { it.members.remove(observer) }
        }

        sessionRepository.save(session)
        eventPublisherService.publishSessionStatus(sessionId, session)
        domainEventLogger.emit(PlayerJoined(sessionId = sessionId, userId = observer.id!!, role = observer.role))
        return sessionWithTeamsAndMembersMapper.toDto(session)
    }

    // --- util

    private fun createRounds(newSession: Session) {
        val cfg = newSession.config
            ?: error("Session.config не должен быть null к этому моменту")
        repeat(cfg.numRounds) { index ->
            newSession.rounds += Round(roundNumber = index + 1, session = newSession)
        }
    }

    private fun createTeams(session: Session) {
        val cfg = session.config
            ?: error("Session.config не должен быть null к этому моменту")

        when (cfg.sessionType) {
            SessionType.FREE_FOR_ALL -> {
                require(cfg.numTeams == 0) {
                    "Для режима FREE_FOR_ALL numTeams должно быть 0 (сейчас ${cfg.numTeams})"
                }
                // команд нет инициализировать нечего
                return
            }

            SessionType.TEAM_BATTLE -> {
                require(cfg.numTeams >= 2) {
                    "Минимальное количество команд для TEAM_BATTLE: 2 (сейчас ${cfg.numTeams})"
                }
            }
        }

        // здесь гарантировано: TEAM_BATTLE и numTeams ≥ 2
        repeat(cfg.numTeams) { index ->
            session.teams += Team(
                name = "Команда №${index + 1}",
                session = session
            )
        }
    }
}
