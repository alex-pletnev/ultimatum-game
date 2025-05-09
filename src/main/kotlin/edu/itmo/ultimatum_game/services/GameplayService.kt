package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.repositories.SessionRepository
import edu.itmo.ultimatum_game.util.logger
import org.springframework.stereotype.Service

@Service
class GameplayService(
    eventPublisher: EventPublisherService,
    sessionRepository: SessionRepository,

) {

    private val logger = logger()

    

}