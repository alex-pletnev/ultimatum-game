package edu.itmo.ultimatum_game.repositories

import edu.itmo.ultimatum_game.model.Session
import org.springframework.data.repository.CrudRepository
import java.util.*

interface SessionRepository : CrudRepository<Session, UUID>