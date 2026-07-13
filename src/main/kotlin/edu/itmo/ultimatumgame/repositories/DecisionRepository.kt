package edu.itmo.ultimatumgame.repositories

import edu.itmo.ultimatumgame.model.Decision
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DecisionRepository : CrudRepository<Decision, UUID> {

    /** Берём сразу все нужные связи, чтобы избежать N+1 при доступе к offer/responder/round. */
    @Query(
        """
        select d
        from Decision d
            left join fetch d.offer o
            left join fetch d.responder r
            left join fetch d.round rd
        where d.session.id = :sessionId
        """
    )
    fun findAllBySessionIdWithRelations(@Param("sessionId") sessionId: UUID): List<Decision>
}
