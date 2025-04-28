package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.exceptions.DuplicateIdException
import edu.itmo.ultimatum_game.exceptions.IdNotFoundException
import edu.itmo.ultimatum_game.model.User
import edu.itmo.ultimatum_game.repositories.UserRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
    private val securityService: SecurityService
) {

    fun getUserById(id: UUID): User =
        userRepository.findById(id).orElseThrow { IdNotFoundException("Пользователь с $id не найден") }

    fun save(user: User): User = userRepository.save(user)

    fun create(user: User): User {
        user.id?.let { id ->
            if (userRepository.existsById(id)) {
                throw DuplicateIdException("Пользователь с $id уже существует")
            }
        }

        return userRepository.save(user)
    }

    //spring security required
    fun getUserDetailService() = this::getUserById

    fun getCurrentUser(): User = getUserById(securityService.getCurrentUserId())


}