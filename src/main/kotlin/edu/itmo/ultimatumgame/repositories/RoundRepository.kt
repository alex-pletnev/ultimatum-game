package edu.itmo.ultimatumgame.repositories

import edu.itmo.ultimatumgame.model.Round
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RoundRepository : CrudRepository<Round, UUID>
