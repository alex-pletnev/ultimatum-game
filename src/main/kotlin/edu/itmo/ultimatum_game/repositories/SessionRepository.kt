package edu.itmo.ultimatum_game.repositories

import edu.itmo.ultimatum_game.model.Session
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SessionRepository : CrudRepository<Session, UUID> {

    fun findAll(pageable: Pageable): Page<Session>

    @Query(
        value ="""
    SELECT *, similarity(display_name, :search) AS sml
    FROM session
    WHERE display_name ILIKE :pattern
    ORDER BY sml DESC
    """,
        countQuery = "SELECT count(*) FROM session WHERE display_name ILIKE :pattern",
        nativeQuery = true
    )
    fun searchByNameTrgm(
        @Param("search") search: String,
        @Param("pattern") pattern: String,
        pageable: Pageable
    ): Page<Session>
}