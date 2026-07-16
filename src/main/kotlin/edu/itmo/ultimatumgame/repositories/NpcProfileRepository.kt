package edu.itmo.ultimatumgame.repositories

import edu.itmo.ultimatumgame.model.NpcProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NpcProfileRepository : JpaRepository<NpcProfile, UUID> {
    fun findByUserId(userId: UUID): NpcProfile?
    fun existsByUserNickname(nickname: String): Boolean
}
