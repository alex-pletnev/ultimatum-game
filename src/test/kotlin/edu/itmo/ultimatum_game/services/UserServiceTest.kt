package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.TestFixtures.user
import edu.itmo.ultimatum_game.exceptions.DuplicateIdException
import edu.itmo.ultimatum_game.exceptions.IdNotFoundException
import edu.itmo.ultimatum_game.repositories.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class UserServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val securityService = mockk<SecurityService>()
    private val service = UserService(userRepository, securityService)

    @Test
    fun `getUserById — возвращает найденного пользователя`() {
        val u = user()
        every { userRepository.findById(u.id!!) } returns Optional.of(u)

        assertSame(u, service.getUserById(u.id!!))
    }

    @Test
    fun `getUserById — бросает IdNotFoundException если не найден`() {
        val id = UUID.randomUUID()
        every { userRepository.findById(id) } returns Optional.empty()

        assertThrows<IdNotFoundException> { service.getUserById(id) }
    }

    @Test
    fun `save — делегирует в userRepository_save`() {
        val u = user()
        every { userRepository.save(u) } returns u

        assertSame(u, service.save(u))
        verify { userRepository.save(u) }
    }

    @Test
    fun `create — сохраняет нового пользователя без id`() {
        val u = user(id = UUID.randomUUID())
        every { userRepository.existsById(u.id!!) } returns false
        every { userRepository.save(u) } returns u

        assertSame(u, service.create(u))
    }

    @Test
    fun `create — бросает DuplicateIdException если id уже занят`() {
        val u = user(id = UUID.randomUUID())
        every { userRepository.existsById(u.id!!) } returns true

        assertThrows<DuplicateIdException> { service.create(u) }
    }

    @Test
    fun `create — если id null, existsById не вызывается и user сохраняется`() {
        val u = user(id = null)
        every { userRepository.save(u) } returns u

        assertSame(u, service.create(u))
        verify(exactly = 0) { userRepository.existsById(any()) }
    }

    @Test
    fun `getUserDetailService — возвращает функцию, которая делегирует в getUserById`() {
        val u = user()
        every { userRepository.findById(u.id!!) } returns Optional.of(u)

        val fn = service.getUserDetailService()
        assertSame(u, fn(u.id!!))
    }

    @Test
    fun `getCurrentUser — берёт id из SecurityService и возвращает пользователя`() {
        val u = user()
        every { securityService.getCurrentUserId() } returns u.id!!
        every { userRepository.findById(u.id!!) } returns Optional.of(u)

        assertEquals(u, service.getCurrentUser())
    }
}
