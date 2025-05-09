package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.dto.requests.CreateSessionRequest
import edu.itmo.ultimatum_game.dto.responses.SessionResponse
import edu.itmo.ultimatum_game.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatum_game.exceptions.IdNotFoundException
import edu.itmo.ultimatum_game.exceptions.SessionJoinRejectedException
import edu.itmo.ultimatum_game.model.Round
import edu.itmo.ultimatum_game.model.Session
import edu.itmo.ultimatum_game.model.SessionType
import edu.itmo.ultimatum_game.model.Team
import edu.itmo.ultimatum_game.repositories.SessionRepository
import edu.itmo.ultimatum_game.repositories.TeamRepository
import edu.itmo.ultimatum_game.util.SessionMapper
import edu.itmo.ultimatum_game.util.SessionWithTeamsAndMembersMapper
import edu.itmo.ultimatum_game.util.logger
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.util.*

@Service
class SessionService(
    private val sessionRepository: SessionRepository,
    private val teamRepository: TeamRepository,
    private val sessionMapper: SessionMapper,
    private val sessionWithTeamsAndMembersMapper: SessionWithTeamsAndMembersMapper,
    private val userService: UserService
) {

    private val logger = logger()

    @Transactional
    fun createSession(createSessionRequest: CreateSessionRequest): SessionResponse {
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
        val dto = sessionMapper.toDto(newSession)
        return dto
    }


    fun getSession(sessionId: UUID): SessionResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { IdNotFoundException("Сессия с $sessionId не найдена") }
        logger.debug("Найдена сессия {}", session)
        val dto = sessionMapper.toDto(session)
        return dto
    }

    //TODO сделать protected
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

    fun getAllSessions(page: Int, pageSize: Int, s: String): Page<SessionResponse> {
        val pageable = createPageable(page, pageSize)
        val sessions: Page<Session> =
            if (s.isBlank()) sessionRepository.findAll(pageable)
            else sessionRepository.searchByNameTrgm(s, "%$s%", pageable)
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
        if (session.members.size >= config.numPlayers) throw SessionJoinRejectedException("В сессии $sessionId достигнуто максимальное количество игроков (${session.members.size} из ${config.numPlayers})")
        if (session.admin == user) throw SessionJoinRejectedException("Вы на можете подключиться к сессии в качестве участника, так как вы ее администратор")
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
        if (session.admin == observer) throw SessionJoinRejectedException("Вы на можете подключиться к сессии в качестве зрителя, так как вы ее администратор")
        session.observers += observer
        if (session.members.remove(observer)) {
            session.teams.forEach { it.members.remove(observer) }
        }

        sessionRepository.save(session)
        return sessionWithTeamsAndMembersMapper.toDto(session)
    }


    // --- util


    private fun createPageable(page: Int, pageSize: Int): Pageable {
        return PageRequest.of(page, pageSize, Sort.by("created_at").descending())
    }

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