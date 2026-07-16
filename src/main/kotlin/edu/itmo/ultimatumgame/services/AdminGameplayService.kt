@file:Suppress("MaxLineLength", "MaximumLineLength", "UnsafeCallOnNullableType")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.model.RoundPhase
import edu.itmo.ultimatumgame.model.SessionState
import edu.itmo.ultimatumgame.repositories.SessionRepository
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.RoundAborted
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
    private val npcService: NpcService,
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
            npcService.playOffers(round)
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

        // Раунд, прерванный админом, сохраняет фазу ABORTED — не переписываем в FINISHED,
        // чтобы история раундов отражала реальное закрытие (T-054).
        if (currentRound.roundPhase != RoundPhase.ABORTED) {
            currentRound.roundPhase = RoundPhase.FINISHED
        }
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
            npcService.playOffers(newRound)
        } else {
            domainEventLogger.emit(SessionClosed(sessionId = sessionId))
        }
    }

    /**
     * Прерывает текущий раунд без перехода к следующему (T-054).
     * После abort'а — новые offers/decisions отклоняются PlayerGameplayService'ом
     * (см. проверку phase внутри sendOffer/makeDecision). Админ вызывает `startNextRound`
     * когда решит продолжить игру.
     *
     * Ошибки:
     * - сессия не RUNNING → IllegalStateException (409 через WebSocketExceptionAdvice)
     * - нет активного раунда → IllegalStateException
     * - раунд уже FINISHED / ABORTED → IllegalStateException
     */
    @Transactional
    fun abortCurrentRound(sessionId: UUID) = withSessionMdc(sessionId) {
        logger.info("Вызван метод abortCurrentRound c sessionId={}", sessionId)
        val session = sessionService.getSessionEntity(sessionId)
        check(session.state == SessionState.RUNNING) {
            "Прерывание раунда возможно только для сессии в состоянии RUNNING, текущее: ${session.state}"
        }
        val currentRound = session.currentRound
            ?: error("Нет активного раунда, который можно прервать")
        check(currentRound.roundPhase != RoundPhase.FINISHED && currentRound.roundPhase != RoundPhase.ABORTED) {
            "Раунд ${currentRound.roundNumber} уже завершён (${currentRound.roundPhase}), прерывать нечего"
        }

        currentRound.roundPhase = RoundPhase.ABORTED
        sessionRepository.save(session)
        logger.info("Раунд {} сессии {} переведён в ABORTED", currentRound.roundNumber, session.id)

        eventPublisherService.publishRoundStatus(sessionId, currentRound)
        domainEventLogger.emit(
            RoundAborted(sessionId = sessionId, roundId = currentRound.id!!, roundNumber = currentRound.roundNumber)
        )
    }
}
