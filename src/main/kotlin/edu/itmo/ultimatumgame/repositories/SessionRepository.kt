package edu.itmo.ultimatumgame.repositories

import edu.itmo.ultimatumgame.model.Session
import edu.itmo.ultimatumgame.model.SessionState
import edu.itmo.ultimatumgame.model.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SessionRepository : CrudRepository<Session, UUID>, JpaSpecificationExecutor<Session> {

    @Query(value = "SELECT 1 FROM session WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun lockById(@Param("id") id: UUID): Int?

    fun existsByIdAndAdminId(id: UUID, userId: UUID): Boolean

    @Query(
        value = """SELECT DISTINCT s FROM Session s LEFT JOIN s.members m LEFT JOIN s.observers o
            WHERE s.admin.id = :userId OR m.id = :userId OR o.id = :userId ORDER BY s.createdAt DESC""",
        countQuery = """SELECT COUNT(DISTINCT s) FROM Session s LEFT JOIN s.members m LEFT JOIN s.observers o
            WHERE s.admin.id = :userId OR m.id = :userId OR o.id = :userId"""
    )
    fun findMine(@Param("userId") userId: UUID, pageable: Pageable): Page<Session>

    fun findAll(pageable: Pageable): Page<Session>

    @Query(
        value = """
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

    fun existsByMembersContainingAndStateNotIn(user: User, states: Collection<SessionState>): Boolean
}
