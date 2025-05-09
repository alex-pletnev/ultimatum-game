package edu.itmo.ultimatum_game.repositories

import edu.itmo.ultimatum_game.model.Team
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TeamRepository : CrudRepository<Team, UUID>