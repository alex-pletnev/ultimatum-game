package edu.itmo.ultimatumgame.repositories

import edu.itmo.ultimatumgame.model.Round
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RoundRepository : CrudRepository<Round, UUID> {

    /**
     * Берём сразу offers + participants раунда. Decisions намеренно НЕ fetch'им
     * тем же запросом — Hibernate бросил бы MultipleBagFetchException при JOIN
     * FETCH двух List-коллекций одновременно. Decisions ленятся; сервисный метод
     * должен вызываться под @Transactional(readOnly = true) для их traverse'а.
     */
    @Query(
        """
        select distinct r
        from Round r
            left join fetch r.offers o
            left join fetch o.proposer
            left join fetch o.responder
        where r.session.id = :sessionId
        """
    )
    fun findAllBySessionIdWithRelations(@Param("sessionId") sessionId: UUID): List<Round>
}
