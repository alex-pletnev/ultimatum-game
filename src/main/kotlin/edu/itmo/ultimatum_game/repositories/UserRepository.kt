package edu.itmo.ultimatum_game.repositories

import edu.itmo.ultimatum_game.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, UUID> {
}