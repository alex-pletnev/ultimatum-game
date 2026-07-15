@file:Suppress("LongParameterList", "LongMethod", "UnsafeCallOnNullableType", "UnnecessaryNotNullOperator")

package edu.itmo.ultimatumgame.services

import edu.itmo.ultimatumgame.dto.requests.CreateOfferCmd
import edu.itmo.ultimatumgame.dto.requests.MakeDecisionCmd
import edu.itmo.ultimatumgame.exceptions.DuplicateIdException
import edu.itmo.ultimatumgame.exceptions.IdNotFoundException
import edu.itmo.ultimatumgame.model.Decision
import edu.itmo.ultimatumgame.model.Offer
import edu.itmo.ultimatumgame.model.RoundPhase
import edu.itmo.ultimatumgame.repositories.DecisionRepository
import edu.itmo.ultimatumgame.repositories.OfferRepository
import edu.itmo.ultimatumgame.repositories.RoundRepository
import edu.itmo.ultimatumgame.repositories.SessionRepository
import edu.itmo.ultimatumgame.util.DecisionMade
import edu.itmo.ultimatumgame.util.DomainEventLogger
import edu.itmo.ultimatumgame.util.OfferSubmitted
import edu.itmo.ultimatumgame.util.RoundClosed
import edu.itmo.ultimatumgame.util.logger
import edu.itmo.ultimatumgame.util.toUuidOrThrow
import edu.itmo.ultimatumgame.util.withSessionMdc
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PlayerGameplayService(
    private val eventPublisher: EventPublisherService,
    private val sessionRepository: SessionRepository,
    private val sessionService: SessionService,
    private val roundRepository: RoundRepository,
    private val offerRepository: OfferRepository,
    private val decisionRepository: DecisionRepository,
    private val userService: UserService,
    private val gameplayService: CoreGameplayService,
    private val statsService: StatsService,
    private val domainEventLogger: DomainEventLogger,
) {

    private val logger = logger()

    @Transactional
    fun sendOffer(sessionId: UUID, playerId: UUID, createOfferCmd: CreateOfferCmd) = withSessionMdc(sessionId) {
        val offerValue = createOfferCmd.amount ?: error("createOfferCmd должен иметь значение на этот момент")
        logger.info("Вызван метод sendOffer с sessionId={} playerId={} amount={}", sessionId, playerId, offerValue)

        val user = userService.getUserById(playerId)
        logger.debug("Получен пользователь {}", user.id)

        val session = sessionService.getSessionEntity(sessionId)
        logger.debug("Получена сессия {} с состоянием {}", session.id, session.state)

        val roundSum = session.config?.roundSum
            ?: error("session.config.roundSum должен быть задан к этому моменту")
        require(offerValue in 0..roundSum) {
            "offerValue должен быть в диапазоне [0, $roundSum], получено $offerValue"
        }

        val round = session.currentRound ?: error("session.currentRound не должен быть null к этому моменту")
        logger.debug("Текущий раунд {} для сессии {}", round.roundNumber, session.id)

        // Раунд принимает офферы только в фазе WAIT_OFFERS (T-054, T-050 контракт).
        check(round.roundPhase == RoundPhase.WAIT_OFFERS) {
            "Оффер нельзя отправить в фазе ${round.roundPhase}, требуется WAIT_OFFERS"
        }

        if (round.offers.any { it.proposer?.id == user.id }) {
            logger.warn("Повторная попытка отправить оффер пользователем {} в раунде {}", user.id, round.roundNumber)
            throw DuplicateIdException("Вы уже отправили offer для этого раунда")
        }

        val offer = offerRepository.save(
            Offer(
                session = session,
                round = round,
                proposer = user,
                offerValue = offerValue,
            )
        )
        round.offers += offer
        roundRepository.save(round)
        logger.info("Создан оффер {} для сессии {}", offer, session.id)

        eventPublisher.publishOfferCreated(sessionId, offer)
        logger.info("Опубликовано событие OfferCreated для сессии {} оффера {}", sessionId, offer.id)

        domainEventLogger.emit(
            OfferSubmitted(
                sessionId = sessionId,
                roundId = round.id!!,
                offerId = offer.id!!,
                proposerId = user.id!!,
                amount = offerValue,
            )
        )

        if (round.offers.size == session.members.size) {
            logger.debug("Все офферы получены для раунда {} сессии {}", round.roundNumber, session.id)
            round.roundPhase = RoundPhase.ALL_OFFERS_RECEIVED
            logger.info("Установлена фаза раунда {}: {}", round.roundNumber, round.roundPhase)

            gameplayService.initWaitDecisionsPhase(session)
            logger.debug("Инициализирована фаза ожидания решений для сессии {}", session.id)

            sessionRepository.save(session)
            logger.info("Сессия {} сохранена после перехода к фазе WAIT_DECISIONS", session.id)

            eventPublisher.publishRoundStatus(sessionId, round)
            logger.info("Опубликован RoundStatus для сессии {} после получения всех офферов", sessionId)
        }
    }

    @Transactional
    fun makeDecision(sessionId: UUID, playerId: UUID, makeDecisionCmd: MakeDecisionCmd) = withSessionMdc(sessionId) {
        val decisionValue = makeDecisionCmd.decision
        val offerId = makeDecisionCmd.offerId.toUuidOrThrow()
        logger.info(
            "Вызван метод makeDecision с sessionId={} playerId={} offerId={} decision={}",
            sessionId,
            playerId,
            offerId,
            decisionValue
        )

        val user = userService.getUserById(playerId)
        logger.debug("Получен пользователь {}", user.id)

        val session = sessionService.getSessionEntity(sessionId)
        logger.debug("Получена сессия {} с состоянием {}", session.id, session.state)

        val round = session.currentRound ?: error("session.currentRound не должен быть null к этому моменту")
        logger.debug("Текущий раунд {} для сессии {}", round.roundNumber, session.id)

        // Раунд принимает решения только в фазе OFFERS_SENT (T-054).
        check(round.roundPhase == RoundPhase.OFFERS_SENT) {
            "Решение нельзя отправить в фазе ${round.roundPhase}, требуется OFFERS_SENT"
        }

        if (round.decisions.any { it.responder?.id == user.id }) {
            logger.warn("Повторная попытка отправить решение пользователем {} в раунде {}", user.id, round.roundNumber)
            throw DuplicateIdException("Вы уже отправили решение для этого раунда")
        }

        val offer = round.offers.find { it.id == offerId }
            ?: run {
                logger.error("Оффер с id={} не найден в раунде {} сессии {}", offerId, round.roundNumber, session.id)
                throw IdNotFoundException("Не найден id офера для makeDecision")
            }
        logger.debug("Найден оффер {} для принятия решения", offer.id)

        val decision = decisionRepository.save(
            Decision(
                session = session,
                round = round,
                responder = user,
                offer = offer,
                decision = decisionValue
            )
        )
        round.decisions += decision
        roundRepository.save(round)
        logger.info("Создано решение {} для сессии {}", decision.id, session.id)

        eventPublisher.publishDecisionMade(sessionId, decision)
        logger.info("Опубликовано событие DecisionMade для сессии {} решения {}", sessionId, decision.id)

        domainEventLogger.emit(
            DecisionMade(
                sessionId = sessionId,
                roundId = round.id!!,
                offerId = offer.id!!,
                responderId = user.id!!,
                accepted = decisionValue,
                amount = offer.offerValue,
            )
        )

        if (round.decisions.size == session.members.size) {
            logger.debug("Все решения получены для раунда {} сессии {}", round.roundNumber, session.id)
            round.roundPhase = RoundPhase.ALL_DECISIONS_RECEIVED
            logger.info("Установлена фаза раунда {}: {}", round.roundNumber, round.roundPhase)

            sessionRepository.save(session)
            logger.info("Сессия {} сохранена после перехода к фазе ALL_DECISIONS_RECEIVED", session.id)

            eventPublisher.publishRoundStatus(sessionId, round)
            logger.info("Опубликован RoundStatus для сессии {} после получения всех решений", sessionId)

            val score = statsService.getSessionStats(sessionId).score
            eventPublisher.publishScoreUpdated(sessionId, score)
            logger.info(
                "Опубликован ScoreUpdated для сессии {} (roundsCompleted={})",
                sessionId,
                score.roundsCompleted
            )

            domainEventLogger.emit(
                RoundClosed(sessionId = sessionId, roundId = round.id!!, roundNumber = round.roundNumber)
            )
        }
    }
}
