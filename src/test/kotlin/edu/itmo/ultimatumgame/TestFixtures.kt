package edu.itmo.ultimatumgame

import edu.itmo.ultimatumgame.model.Decision
import edu.itmo.ultimatumgame.model.Offer
import edu.itmo.ultimatumgame.model.Role
import edu.itmo.ultimatumgame.model.Round
import edu.itmo.ultimatumgame.model.RoundPhase
import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.model.SessionConfig
import edu.itmo.ultimatumgame.model.SessionState
import edu.itmo.ultimatumgame.model.SessionType
import edu.itmo.ultimatumgame.model.Team
import edu.itmo.ultimatumgame.model.User
import java.util.Date
import java.util.UUID

/**
 * Builders для unit-тестов. Возвращают entity-объекты с уже проставленным id
 * (в JPA-раскладке id проставляется базой; в тестах мы работаем без БД,
 * поэтому проставляем сами).
 */
object TestFixtures {

    fun user(
        id: UUID? = UUID.randomUUID(),
        nickname: String = "user-${id.toString().take(8)}",
        role: Role = Role.PLAYER,
        createdAt: Date = Date(0),
    ): User = User(id = id, nickname = nickname, role = role, createdAt = createdAt)

    fun sessionConfig(
        sessionType: SessionType = SessionType.FREE_FOR_ALL,
        numRounds: Int = 3,
        numTeams: Int = 0,
        numPlayers: Int = 4,
        roundSum: Int = 100,
        timeoutMoveSec: Int = 60,
    ): SessionConfig = SessionConfig(
        sessionType = sessionType,
        numRounds = numRounds,
        numTeams = numTeams,
        numPlayers = numPlayers,
        roundSum = roundSum,
        timeoutMoveSec = timeoutMoveSec,
    )

    fun session(
        id: UUID = UUID.randomUUID(),
        displayName: String = "session-${id.toString().take(8)}",
        state: SessionState = SessionState.CREATED,
        admin: User = user(role = Role.ADMIN),
        config: SessionConfig = sessionConfig(),
        openToConnect: Boolean = true,
        members: MutableSet<User> = mutableSetOf(),
        observers: MutableSet<User> = mutableSetOf(),
        teams: MutableSet<Team> = mutableSetOf(),
        currentRound: Round? = null,
    ): Session = Session(
        id = id,
        displayName = displayName,
        state = state,
        createdAt = Date(0),
        admin = admin,
        openToConnect = openToConnect,
        currentRound = currentRound,
        rounds = mutableSetOf(),
        config = config,
        teams = teams,
        members = members,
        observers = observers,
    )

    fun round(
        id: UUID = UUID.randomUUID(),
        session: Session? = null,
        roundNumber: Int = 1,
        roundPhase: RoundPhase = RoundPhase.CREATED,
        offers: MutableList<Offer> = mutableListOf(),
        decisions: MutableList<Decision> = mutableListOf(),
    ): Round = Round(
        id = id,
        session = session,
        roundNumber = roundNumber,
        roundPhase = roundPhase,
        offers = offers,
        decisions = decisions,
    )

    fun offer(
        id: UUID = UUID.randomUUID(),
        proposer: User,
        responder: User? = null,
        session: Session? = null,
        round: Round? = null,
        offerValue: Int = 50,
    ): Offer = Offer(
        id = id,
        session = session,
        round = round,
        proposer = proposer,
        responder = responder,
        offerValue = offerValue,
    )

    fun team(
        id: UUID = UUID.randomUUID(),
        name: String = "team-${id.toString().take(8)}",
        members: MutableSet<User> = mutableSetOf(),
        session: Session? = null,
    ): Team = Team(id = id, name = name, members = members, session = session)
}
