package edu.itmo.ultimatum_game.repositories

import edu.itmo.ultimatum_game.model.Round
import org.springframework.data.repository.CrudRepository
import java.util.*

interface RoundRepository : CrudRepository<Round, UUID>