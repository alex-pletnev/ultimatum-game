package edu.itmo.ultimatumgame.repositories

import edu.itmo.ultimatumgame.model.Round
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RoundRepository : CrudRepository<Round, UUID> {

    /** Берём сразу все офферы и решения раунда, чтобы избежать N+1 при обходе rounds. */
    @Query(
        """
        select distinct r
        from Round r
            left join fetch r.offers o
            left join fetch o.proposer
            left join fetch o.responder
            left join fetch r.decisions d
            left join fetch d.responder
        where r.session.id = :sessionId
        """
    )
    fun findAllBySessionIdWithRelations(@Param("sessionId") sessionId: UUID): List<Round>
}
