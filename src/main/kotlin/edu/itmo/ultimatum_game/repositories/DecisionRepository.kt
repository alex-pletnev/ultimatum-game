package edu.itmo.ultimatum_game.repositories

import edu.itmo.ultimatum_game.model.Decision
import org.springframework.data.repository.CrudRepository
import java.util.*

interface DecisionRepository : CrudRepository<Decision, UUID>