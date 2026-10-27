package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.requests.BulkNpcsRequest
import edu.itmo.ultimatumgame.dto.requests.CreateSessionRequest
import edu.itmo.ultimatumgame.dto.requests.SessionConfigDto
import edu.itmo.ultimatumgame.dto.requests.SoloSessionRequest
import edu.itmo.ultimatumgame.dto.responses.SessionWithTeamsAndMembersResponse
import edu.itmo.ultimatumgame.exceptions.SessionJoinRejectedException
import edu.itmo.ultimatumgame.model.NpcParams
import edu.itmo.ultimatumgame.model.NpcStrategy
import edu.itmo.ultimatumgame.model.SessionState
import edu.itmo.ultimatumgame.model.SessionType
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.UUID

private const val REMATCH_NAME_LIMIT = 89
private const val NPC_BATCH_SIZE = 100

@Service
class SessionScenariosService(
    private val sessions: SessionService,
    private val gameplay: AdminGameplayService,
    private val npcService: NpcService,
    private val events: EventPublisherService,
) {
    @Transactional
    fun setAutopilot(id: UUID, playerId: UUID, enabled: Boolean): SessionWithTeamsAndMembersResponse {
        val session = sessions.getSessionForUpdate(id)
        check(session.state == SessionState.RUNNING) { "Автопилот доступен во время игры" }
        require(session.members.any { it.id == playerId && it.role != edu.itmo.ultimatumgame.model.Role.NPC }) {
            "Участник не найден или уже является ботом"
        }
        if (enabled) session.autopilotPlayerIds.add(playerId) else session.autopilotPlayerIds.remove(playerId)
        session.currentRound?.let { round ->
            npcService.playOffers(round)
            npcService.playDecisions(round)
        }
        events.publishSessionStatus(id, session)
        return sessions.getSessionWithTeamsAndMembers(id)
    }

    @Transactional
    fun fillRandomNpcs(id: UUID): SessionWithTeamsAndMembersResponse {
        val session = sessions.getSessionForUpdate(id)
        if (session.state != SessionState.CREATED) {
            throw SessionJoinRejectedException("Заполнить ботами можно только лобби до начала игры")
        }
        val config = requireNotNull(session.config)
        val capacity = if (config.sessionType == SessionType.TEAM_BATTLE) {
            minOf(config.numPlayers, session.teams.size * (config.numPlayers / 2))
        } else {
            config.numPlayers
        }
        val remaining = (capacity - session.members.size).coerceAtLeast(0)
        if (remaining == 0) return sessions.getSessionWithTeamsAndMembers(id)
        if (!session.openToConnect) throw SessionJoinRejectedException("Лобби закрыто для подключения")
        val strategies = NpcStrategy.entries.shuffled()
        List(remaining) { strategies[it % strategies.size] }.chunked(NPC_BATCH_SIZE).forEach { batch ->
            batch.groupingBy { it }.eachCount().forEach { (strategy, count) ->
                sessions.bulkCreateAndJoinNpcs(id, BulkNpcsRequest(count, strategy, defaults(strategy)))
            }
        }
        return sessions.getSessionWithTeamsAndMembers(id)
    }

    @Transactional
    fun solo(request: SoloSessionRequest): SessionWithTeamsAndMembersResponse {
        require(request.sessionType != SessionType.TEAM_BATTLE) { "Соло доступно без команд" }
        val game = sessions.createSession(
            CreateSessionRequest(
                displayName = if (request.sessionType == SessionType.DICTATOR) "Диктатор · соло" else "Игра с ботом",
                hostPlays = true,
                config = SessionConfigDto(
                    sessionType = request.sessionType,
                    numRounds = request.numRounds,
                    numPlayers = 2,
                    roundSum = 100,
                    timeoutMoveSec = 60,
                ),
            )
        )
        sessions.bulkCreateAndJoinNpcs(
            game.id,
            BulkNpcsRequest(
                count = 1,
                strategy = request.strategy,
                params = defaults(request.strategy),
            )
        )
        gameplay.startSession(game.id)
        return sessions.getSessionWithTeamsAndMembers(game.id)
    }

    @Transactional
    fun rematch(id: UUID): SessionWithTeamsAndMembersResponse {
        val previous = sessions.getSessionEntity(id)
        val config = requireNotNull(previous.config)
        return sessions.createSession(
            CreateSessionRequest(
                displayName = previous.displayName.take(REMATCH_NAME_LIMIT) + " · реванш",
                hostPlays = previous.members.any { it.id == previous.admin?.id },
                config = SessionConfigDto(
                    config.sessionType,
                    config.numRounds,
                    config.numTeams,
                    config.numPlayers,
                    config.roundSum,
                    config.timeoutMoveSec,
                    config.autoAdvanceRounds,
                    config.anonymous,
                ),
            )
        )
    }

    private fun defaults(strategy: NpcStrategy): NpcParams = when (strategy) {
        NpcStrategy.FAIR -> NpcParams.Fair()
        NpcStrategy.SELFISH -> NpcParams.Selfish()
        NpcStrategy.RANDOM -> NpcParams.Random()
        NpcStrategy.VENGEFUL -> NpcParams.Vengeful()
        NpcStrategy.ADAPTIVE -> NpcParams.Adaptive()
    }
}
