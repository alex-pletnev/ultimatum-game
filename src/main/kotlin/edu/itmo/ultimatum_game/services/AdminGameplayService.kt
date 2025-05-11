package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.model.RoundPhase
import edu.itmo.ultimatum_game.model.SessionState
import edu.itmo.ultimatum_game.repositories.SessionRepository
import edu.itmo.ultimatum_game.util.logger
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.*

@Service
class AdminGameplayService(
    private val sessionService: SessionService,
    private val sessionRepository: SessionRepository,
    private val eventPublisherService: EventPublisherService
) {

    private val logger = logger()

    @Transactional
    fun startSession(sessionId: UUID) {
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

        eventPublisherService.publishRoundStatus(session.id, session.currentRound)
        logger.info("Опубликован RoundStatus для сессии {}", session.id)
    }

    @Transactional
    fun closeSession(sessionId: UUID) {
        logger.info("Вызван метод closeSession c sessionId={}", sessionId)
        val session = sessionService.getSessionEntity(sessionId)
        logger.debug("Закрытие сессии {} для подключений", session.id)

        session.openToConnect = false
        sessionRepository.save(session)
        logger.info("Сессия {} закрыта для новых подключений", session.id)

        eventPublisherService.publishRoundStatus(session.id, session.currentRound)
        logger.info("Опубликован RoundStatus для сессии {} после закрытия", session.id)
    }

    @Transactional
    fun openSession(sessionId: UUID) {
        logger.info("Вызван метод openSession c sessionId={}", sessionId)
        val session = sessionService.getSessionEntity(sessionId)
        logger.debug("Открытие сессии {} для подключений", session.id)

        session.openToConnect = true
        sessionRepository.save(session)
        logger.info("Сессия {} открыта для новых подключений", session.id)

        eventPublisherService.publishRoundStatus(session.id, session.currentRound)
        logger.info("Опубликован RoundStatus для сессии {} после открытия", session.id)
    }

    @Transactional
    fun abortSession(sessionId: UUID) {
        logger.info("Вызван метод abortSession c sessionId={}", sessionId)
        val session = sessionService.getSessionEntity(sessionId)
        logger.debug("Прерывание сессии {}", session.id)

        session.state = SessionState.ABORTED
        session.openToConnect = false
        sessionRepository.save(session)
        logger.info("Сессия {} прервана и закрыта для подключений", session.id)

        eventPublisherService.publishRoundStatus(session.id, session.currentRound)
        logger.info("Опубликован RoundStatus для сессии {} после прерывания", session.id)
    }

    @Transactional
    fun startNextRound(sessionId: UUID) {
        logger.info("Вызван метод startNextRound c sessionId={}", sessionId)
        val session = sessionService.getSessionEntity(sessionId)
        val currentRound = session.currentRound ?: error("Текущий раунд не должен быть null на данном этапе")
        logger.debug("Завершение раунда {} для сессии {}", currentRound.roundNumber, session.id)

        currentRound.roundPhase = RoundPhase.FINISHED
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

        eventPublisherService.publishRoundStatus(session.id, session.currentRound)
        logger.info("Опубликован RoundStatus для сессии {} после запуска нового раунда", session.id)
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
