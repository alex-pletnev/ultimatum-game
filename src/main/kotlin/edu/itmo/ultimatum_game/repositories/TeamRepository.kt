package edu.itmo.ultimatum_game.repositories

import edu.itmo.ultimatum_game.model.Team
import org.springframework.data.repository.CrudRepository
import java.util.*

interface TeamRepository : CrudRepository<Team, UUID>