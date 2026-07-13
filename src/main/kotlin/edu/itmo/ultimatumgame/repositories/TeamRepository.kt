package edu.itmo.ultimatumgame.repositories

import edu.itmo.ultimatumgame.model.Team
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TeamRepository : CrudRepository<Team, UUID>
