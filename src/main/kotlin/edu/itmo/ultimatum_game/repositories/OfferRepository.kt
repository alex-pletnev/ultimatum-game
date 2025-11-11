package edu.itmo.ultimatum_game.repositories

import edu.itmo.ultimatum_game.model.Offer
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface OfferRepository : CrudRepository<Offer, UUID> {

    /** Берём сразу все нужные связи, чтобы избежать N+1 */
    @Query(
        """
        select o
        from Offer o
            left join fetch o.proposer p
            left join fetch o.responder r
            left join fetch o.round rd
        where o.session.id = :sessionId
        """
    )
    fun findAllBySessionIdWithRelations(@Param("sessionId") sessionId: UUID): List<Offer>
}