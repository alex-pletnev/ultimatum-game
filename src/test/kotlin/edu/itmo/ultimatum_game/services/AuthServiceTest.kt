package edu.itmo.ultimatum_game.services

import edu.itmo.ultimatum_game.TestFixtures.user
import edu.itmo.ultimatum_game.dto.requests.AuthenticateUserRequest
import edu.itmo.ultimatum_game.dto.requests.CreateUserRequest
import edu.itmo.ultimatum_game.exceptions.UserRoleNotAllowedException
import edu.itmo.ultimatum_game.model.Role
import edu.itmo.ultimatum_game.model.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthServiceTest {

    private val jwtService = mockk<JwtService>()
    private val userService = mockk<UserService>()
    private val service = AuthService(jwtService, userService)

    // Держим ссылку на текущего "пользователя" для эмуляции getUserDetailService
    private var loginTarget: User? = null
    private fun lookupUserById(id: UUID): User =
        loginTarget?.takeIf { it.id == id } ?: error("miss")

    @Test
    fun `quickLogin — находит пользователя и возвращает сгенерированный токен`() {
        val u = user()
        loginTarget = u
        every { userService.getUserDetailService() } returns ::lookupUserById
        every { jwtService.generateToken(u) } returns "tok"

        val resp = service.quickLogin(AuthenticateUserRequest(u.id!!))

        assertEquals("tok", resp.token)
    }

    @Test
    fun `quickRegister — создаёт пользователя_PLAYER и возвращает токен`() {
        val savedUser = user(role = Role.PLAYER)
        val userSlot = slot<User>()
        every { userService.create(capture(userSlot)) } returns savedUser
        every { jwtService.generateToken(savedUser) } returns "new-tok"

        val resp = service.quickRegister(CreateUserRequest(nickname = "Alice", role = Role.PLAYER))

        assertEquals("new-tok", resp.token)
        assertEquals("Alice", userSlot.captured.nickname)
        assertEquals(Role.PLAYER, userSlot.captured.role)
    }

    @Test
    fun `quickRegister — бросает UserRoleNotAllowedException при роли NPC`() {
        assertThrows<UserRoleNotAllowedException> {
            service.quickRegister(CreateUserRequest(nickname = "NPC-guy", role = Role.NPC))
        }
    }

    @Test
    fun `quickRegister — ADMIN разрешён`() {
        val savedUser = user(role = Role.ADMIN)
        every { userService.create(any()) } returns savedUser
        every { jwtService.generateToken(savedUser) } returns "admin-tok"

        val resp = service.quickRegister(CreateUserRequest(nickname = "root", role = Role.ADMIN))
        assertEquals("admin-tok", resp.token)
    }
}
