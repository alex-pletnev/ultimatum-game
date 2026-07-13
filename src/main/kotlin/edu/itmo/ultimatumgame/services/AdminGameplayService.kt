@file:Suppress("MaxLineLength", "MaximumLineLength", "UnsafeCallOnNullableType")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.model.RoundPhase
import edu.itmo.ultimatumgame.model.SessionState
import edu.itmo.ultimatumgame.repositories.SessionRepository
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.RoundClosed
import edu.itmo.ultimatumgame.util.RoundStarted
import edu.itmo.ultimatumgame.util.SessionAborted
import edu.itmo.ultimatumgame.util.SessionClosed
import edu.itmo.ultimatumgame.util.SessionStarted
import edu.itmo.ultimatumgame.util.logger
import edu.itmo.ultimatumgame.util.withSessionMdc
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AdminGameplayService(
    private val sessionService: SessionService,
    private val sessionRepository: SessionRepository,
    private val eventPublisherService: EventPublisherService,
    private val domainEventLogger: DomainEventLogger,
) {

    private val logger = logger()

    @Transactional
    fun startSession(sessionId: UUID) = withSessionMdc(sessionId) {
        logger.info("Вызван метод startSession c sessionId={}", sessionId)
        val session = sessionService.getSessionEntity(sessionId)
        logger.debug("Получена сущность сессии {} с состоянием {}", session.id, session.state)

        session.state = SessionState.RUNNING
        session.currentRound = session.rounds.find { it.roundNumber == 1 }
        session.currentRound?.let {
            it.roundPhase = RoundPhase.WAIT_OFFERS
            logger.debug("Инициализирован раунд {} с фазой {}", it.roundNumber, it.roundPhase)
        }
        session.openToConnect = false
        sessionRepository.save(session)
        logger.info("Сессия {} запущена и сохранена со статусом {}", session.id, session.state)

        eventPublisherService.publishSessionStatus(sessionId, session)
        logger.info("Опубликован RoundStatus для сессии {}", sessionId)
        domainEventLogger.emit(SessionStarted(sessionId = sessionId, playerCount = session.members.size))
        session.currentRound?.let { round ->
            domainEventLogger.emit(
                RoundStarted(sessionId = sessionId, roundId = round.id!!, roundNumber = round.roundNumber)
            )
        }
    }

    @Transactional
    fun closeSession(sessionId: UUID) = withSessionMdc(sessionId) {
        logger.info("Вызван метод closeSession c sessionId={}", sessionId)
        val session = sessionService.getSessionEntity(sessionId)
        logger.debug("Закрытие сессии {} для подключений", session.id)

        session.openToConnect = false
        sessionRepository.save(session)
        logger.info("Сессия {} закрыта для новых подключений", session.id)

        eventPublisherService.publishSessionStatus(sessionId, session)
        logger.info("Опубликован RoundStatus для сессии {} после закрытия", sessionId)
    }

    @Transactional
    fun openSession(sessionId: UUID) = withSessionMdc(sessionId) {
        logger.info("Вызван метод openSession c sessionId={}", sessionId)
        val session = sessionService.getSessionEntity(sessionId)
        logger.debug("Открытие сессии {} для подключений", session.id)

        session.openToConnect = true
        sessionRepository.save(session)
        logger.info("Сессия {} открыта для новых подключений", session.id)

        eventPublisherService.publishSessionStatus(sessionId, session)
        logger.info("Опубликован RoundStatus для сессии {} после открытия", sessionId)
    }

    @Transactional
    fun abortSession(sessionId: UUID) = withSessionMdc(sessionId) {
        logger.info("Вызван метод abortSession c sessionId={}", sessionId)
        val session = sessionService.getSessionEntity(sessionId)
        logger.debug("Прерывание сессии {}", session.id)

        session.state = SessionState.ABORTED
        session.openToConnect = false
        sessionRepository.save(session)
        logger.info("Сессия {} прервана и закрыта для подключений", session.id)

        session.currentRound?.let { round ->
            eventPublisherService.publishRoundStatus(sessionId, round)
            logger.info("Опубликован RoundStatus для сессии {} после прерывания", sessionId)
        } ?: logger.info("Abort сессии {} без активного раунда — RoundStatus не публикуется", sessionId)
        domainEventLogger.emit(SessionAborted(sessionId = sessionId))
    }

    @Transactional
    fun startNextRound(sessionId: UUID) = withSessionMdc(sessionId) {
        logger.info("Вызван метод startNextRound c sessionId={}", sessionId)
        val session = sessionService.getSessionEntity(sessionId)
        val currentRound = session.currentRound ?: error("Текущий раунд не должен быть null на данном этапе")
        logger.debug("Завершение раунда {} для сессии {}", currentRound.roundNumber, session.id)

        currentRound.roundPhase = RoundPhase.FINISHED
        val closedRoundId = currentRound.id!!
        val closedRoundNumber = currentRound.roundNumber
        val nextRoundNumber = currentRound.roundNumber + 1
        val newRound = session.rounds.find { it.roundNumber == nextRoundNumber }
        if (newRound == null) {
            session.state = SessionState.FINISHED
            logger.info("Больше нет раундов, сессия {} отмечена как FINISHED", session.id)
        } else {
            newRound.roundPhase = RoundPhase.WAIT_OFFERS
            session.currentRound = newRound
            logger.info("Сессия {} перешла к следующему раунду {}", session.id, newRound.roundNumber)
        }
        sessionRepository.save(session)
        logger.info("Сессия {} сохранена после перехода между раундами", session.id)

        // session.currentRound здесь: либо newRound (перешли), либо старый currentRound c phase=FINISHED (кончились раунды) — не null
        eventPublisherService.publishRoundStatus(sessionId, session.currentRound!!)
        logger.info("Опубликован RoundStatus для сессии {} после запуска нового раунда", sessionId)

        domainEventLogger.emit(
            RoundClosed(sessionId = sessionId, roundId = closedRoundId, roundNumber = closedRoundNumber)
        )
        if (newRound != null) {
            domainEventLogger.emit(
                RoundStarted(sessionId = sessionId, roundId = newRound.id!!, roundNumber = newRound.roundNumber)
            )
        } else {
            domainEventLogger.emit(SessionClosed(sessionId = sessionId))
        }
    }

    fun abortCurrentRound() {
        logger.info("Вызван метод abortCurrentRound")
        TODO()
    }

    fun pauseRound() {
        logger.info("Вызван метод pauseRound (бонус)")
        TODO("бонусом")
    }
}
