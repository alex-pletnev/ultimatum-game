package edu.itmo.ultimatumgame.repositories

import edu.itmo.ultimatumgame.model.Decision
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DecisionRepository : CrudRepository<Decision, UUID> {

    fun findBySessionId(sessionId: UUID): List<Decision>
}
