package edu.itmo.ultimatum_game.repositories

import edu.itmo.ultimatum_game.model.Round
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RoundRepository : CrudRepository<Round, UUID>