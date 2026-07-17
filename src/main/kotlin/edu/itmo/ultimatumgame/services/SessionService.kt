@file:Suppress(
    "MaxLineLength",
    "MaximumLineLength",
    "TooManyFunctions",
    "ThrowsCount",
    "LongParameterList",
    "UnsafeCallOnNullableType",
)

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.requests.BulkNpcsRequest
import edu.itmo.ultimatumgame.dto.requests.CreateSessionRequest
import edu.itmo.ultimatumgame.dto.responses.BulkNpcsResponse
import edu.itmo.ultimatumgame.dto.responses.MyRole
import edu.itmo.ultimatumgame.dto.responses.NpcProfileResponse
import edu.itmo.ultimatumgame.dto.responses.PendingAction
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
import edu.itmo.ultimatumgame.model.Team
import edu.itmo.ultimatumgame.model.User
import edu.itmo.ultimatumgame.repositories.NpcProfileRepository
import edu.itmo.ultimatumgame.repositories.RoundRepository
import edu.itmo.ultimatumgame.repositories.SessionRepository
import edu.itmo.ultimatumgame.repositories.UserRepository
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.NpcJoined
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
import org.springframework.data.jpa.domain.Specification
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
    private val npcProfileRepository: NpcProfileRepository,
    private val userRepository: UserRepository,
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

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
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
        // null — broadcast / system-каналы / anonymous; DTO возвращается без hints
        // (default'ы `NONE` / `emptyList()` из RoundResponse).
        val userId = securityService.getCurrentUserIdOrNull() ?: return dto
        // T-072: myRole / myPendingActions вынесены из primary constructor —
        // присваиваем напрямую вместо data class .copy(...).
        dto.myRole = computeMyRole(round, userId)
        dto.myPendingActions = computeMyPendingActions(round, userId, members)
        return dto
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

    fun getAllSessions(
        page: Int,
        pageSize: Int,
        s: String,
        state: SessionState? = null,
        sessionType: SessionType? = null,
        openToConnect: Boolean? = null,
    ): Page<SessionResponse> {
        val pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending())
        val hasFilters = state != null || sessionType != null || openToConnect != null

        // Backwards-compatible: если фильтры не заданы и есть search — сохраняем pg_trgm-путь
        // с ранжированием по similarity (T-057).
        val sessions: Page<Session> = when {
            !hasFilters && s.isBlank() -> sessionRepository.findAll(pageable)
            !hasFilters && s.isNotBlank() -> sessionRepository.searchByNameTrgm(
                s,
                "%$s%",
                PageRequest.of(page, pageSize, Sort.by("created_at").descending())
            )
            else -> sessionRepository.findAll(
                buildSessionFilterSpec(s, state, sessionType, openToConnect),
                pageable,
            )
        }
        val response = sessions.map { sessionMapper.toDto(it) }
        logger.info(
            "Search '{}', filters state={} type={} openToConnect={} → {} сессий",
            s,
            state,
            sessionType,
            openToConnect,
            response.numberOfElements,
        )
        return response
    }

    /**
     * Собирает [Specification] по опциональным фильтрам (T-057). Все условия AND.
     * Search по displayName — ILIKE (без pg_trgm ranking, потому что Specification API это не даёт;
     * pg_trgm сохраняем только когда фильтров нет — см. [getAllSessions]).
     */
    private fun buildSessionFilterSpec(
        s: String,
        state: SessionState?,
        sessionType: SessionType?,
        openToConnect: Boolean?,
    ): Specification<Session> = Specification { root, _, cb ->
        val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
        if (s.isNotBlank()) {
            predicates += cb.like(cb.lower(root.get("displayName")), "%${s.lowercase()}%")
        }
        state?.let { predicates += cb.equal(root.get<SessionState>("state"), it) }
        openToConnect?.let { predicates += cb.equal(root.get<Boolean>("openToConnect"), it) }
        sessionType?.let {
            predicates += cb.equal(root.get<Any>("config").get<SessionType>("sessionType"), it)
        }
        // toTypedArray()+spread — единственный способ передать varargs в CriteriaBuilder.and().
        @Suppress("SpreadOperator")
        cb.and(*predicates.toTypedArray())
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
        } else {
            session.members += user
            session.observers.remove(user)
        }
        closeIfFull(session, config)
        sessionRepository.save(session)
        eventPublisherService.publishSessionStatus(sessionId, session)
        domainEventLogger.emit(PlayerJoined(sessionId = sessionId, userId = user.id!!, role = user.role))
        val dto = sessionWithTeamsAndMembersMapper.toDto(session)
        return dto
    }

    @Transactional
    fun addNpcMember(sessionId: UUID, npcId: UUID, teamId: UUID?): SessionWithTeamsAndMembersResponse {
        val session = getSessionEntity(sessionId)
        val config = session.config
            ?: error("Session.config не должен быть null к этому моменту")
        check(session.state == SessionState.CREATED && session.openToConnect) {
            "join-npc доступен только для сессий CREATED + openToConnect"
        }
        val profile = npcProfileRepository.findById(npcId)
            .orElseThrow { IdNotFoundException("NPC $npcId не найден") }
        if (session.members.contains(profile.user)) {
            return sessionWithTeamsAndMembersMapper.toDto(session)
        }
        require(session.members.size < config.numPlayers) {
            "В сессии $sessionId достигнуто максимальное количество игроков"
        }
        if (config.sessionType == SessionType.TEAM_BATTLE) {
            val tId = teamId ?: error("Для TEAM_BATTLE обязательно указывать teamId")
            val team: Team = session.teams.find { it.id == tId }
                ?: error("В сессии $sessionId не найдена команда $tId")
            team.members += profile.user
        }
        session.members += profile.user
        closeIfFull(session, config)
        sessionRepository.save(session)
        eventPublisherService.publishSessionStatus(sessionId, session)
        domainEventLogger.emit(NpcJoined(sessionId, profile.user.id!!, profile.strategy.name))
        return sessionWithTeamsAndMembersMapper.toDto(session)
    }

    @Transactional
    fun bulkCreateAndJoinNpcs(sessionId: UUID, req: BulkNpcsRequest): BulkNpcsResponse {
        val session = getSessionEntity(sessionId)
        val config = session.config
            ?: error("Session.config не должен быть null к этому моменту")
        check(session.state == SessionState.CREATED && session.openToConnect) {
            "bulk-npcs доступен только для сессий CREATED + openToConnect"
        }
        require(req.count in 1..MAX_BULK_NPC_COUNT) { "count должен быть в [1..$MAX_BULK_NPC_COUNT]" }
        require(session.members.size + req.count <= config.numPlayers) {
            "count + members больше numPlayers"
        }
        require(paramsMatchStrategy(req.strategy, req.params)) {
            "params не соответствуют strategy=${req.strategy}"
        }
        // T-087: раскладка NPC по командам считается ДО создания сущностей,
        // чтобы round-robin учитывал уже назначенные (иначе 3 NPC на 2 пустые
        // команды дали бы 3/0 вместо 2/1).
        val assignedTeams = planTeamAssignments(session, config, req)
        val created = (0 until req.count).map { i ->
            val nick = "NPC-${req.strategy}-${UUID.randomUUID().toString().take(BULK_NICK_SUFFIX_LEN)}"
            val user = userRepository.save(User(nickname = nick, role = Role.NPC))
            val seed = req.seedBase?.plus(i.toLong())
            npcProfileRepository.save(
                NpcProfile(user = user, strategy = req.strategy, params = req.params, seed = seed)
            )
        }
        created.forEachIndexed { i, profile ->
            session.members += profile.user
            assignedTeams[i]?.members?.add(profile.user)
            domainEventLogger.emit(NpcJoined(sessionId, profile.user.id!!, profile.strategy.name))
        }
        closeIfFull(session, config)
        sessionRepository.save(session)
        eventPublisherService.publishSessionStatus(sessionId, session)
        val sessionDto = sessionWithTeamsAndMembersMapper.toDto(session)
        val npcs = created.map { p ->
            NpcProfileResponse(
                id = p.id!!,
                userId = p.user.id!!,
                nickname = p.user.nickname,
                strategy = p.strategy,
                params = p.params,
                seed = p.seed,
                createdAt = p.createdAt.toInstant(),
            )
        }
        return BulkNpcsResponse(sessionDto, npcs)
    }

    /**
     * T-093: если после инкремента members сессия заполнилась —
     * авто-закрываем её (`openToConnect = false`), чтобы фильтр
     * `GET /session?openToConnect=true` не возвращал полные сессии.
     * `sessionStatus` в WS публикуется вызывающим методом сразу после save
     * — состояние `openToConnect=false` попадёт во фронт-подписки одним broadcast'ом.
     */
    private fun closeIfFull(session: Session, config: edu.itmo.ultimatumgame.model.SessionConfig) {
        if (session.openToConnect && session.members.size >= config.numPlayers) {
            session.openToConnect = false
        }
    }

    /**
     * T-087: раскладка NPC по командам для bulk-create.
     *
     * - FREE_FOR_ALL: `teamId` должен быть null; для каждого NPC — null (в команду не кладём).
     * - TEAM_BATTLE + teamId != null: команда должна существовать; все N NPC в неё.
     * - TEAM_BATTLE + teamId == null: round-robin — на каждой итерации берём команду с
     *   наименьшим (текущий размер + уже назначенные в этом bulk) числом членов.
     */
    private fun planTeamAssignments(
        session: Session,
        config: edu.itmo.ultimatumgame.model.SessionConfig,
        req: BulkNpcsRequest,
    ): List<Team?> = when (config.sessionType) {
        SessionType.FREE_FOR_ALL -> {
            require(req.teamId == null) { "teamId недопустим для FREE_FOR_ALL" }
            List(req.count) { null }
        }
        SessionType.TEAM_BATTLE -> {
            if (req.teamId != null) {
                val team = session.teams.find { it.id == req.teamId }
                    ?: throw IdNotFoundException(
                        "В сессии ${session.id} не найдена команда ${req.teamId}"
                    )
                List(req.count) { team }
            } else {
                val planned = session.teams.associateWith { it.members.size }.toMutableMap()
                List(req.count) {
                    val target = planned.minBy { (_, size) -> size }.key
                    planned[target] = planned.getValue(target) + 1
                    target
                }
            }
        }
    }

    private fun paramsMatchStrategy(strategy: NpcStrategy, p: NpcParams): Boolean = when (strategy) {
        NpcStrategy.FAIR -> p is NpcParams.Fair
        NpcStrategy.SELFISH -> p is NpcParams.Selfish
        NpcStrategy.RANDOM -> p is NpcParams.Random
        NpcStrategy.VENGEFUL -> p is NpcParams.Vengeful
        NpcStrategy.ADAPTIVE -> p is NpcParams.Adaptive
    }

    private companion object {
        const val MAX_BULK_NPC_COUNT = 100
        const val BULK_NICK_SUFFIX_LEN = 6
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
