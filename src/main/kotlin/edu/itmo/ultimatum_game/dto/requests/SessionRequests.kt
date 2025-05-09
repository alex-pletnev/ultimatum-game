//package edu.itmo.ultimatum_game.dto.requests
//
//import edu.itmo.ultimatum_game.model.SessionType
//import jakarta.validation.constraints.*
//
//data class CreateSessionRequest(
//    @field:NotBlank(message = "displayName — это обязательное поле")
//    @field:Size(min = 3, max = 100, message = "Длина displayName должна быть от 3 до 100 символов")
//    val displayName: String,
//    val config: SessionConfigDto,
//)
//
//data class SessionConfigDto(
//    val sessionType: SessionType? = null,
//
//    @field:NotNull(message = "numRounds — это обязательное поле")
//    @field:Min(1, message = "Минимальное количество раундов — 1")
//    @field:Max(10, message = "Максимальное количество раундов — 10")
//    val numRounds: Int,
//
//    @field:PositiveOrZero(message = "numTeams должно быть 0 или положительным числом")
//    @field:Max(5, message = "Максимальное количество команд — 5")
//    val numTeams: Int? = 0,
//
//    @field:NotNull(message = "numPlayers — это обязательное поле")
//    @field:Min(2, message = "Минимальное количество игроков — 2")
//    @field:Max(120, message = "Максимальное количество игроков — 120")
//    val numPlayers: Int,
//
//    @field:NotNull(message = "roundSum — это обязательное поле")
//    @field:Min(10, message = "Минимальный ресурс на раунд — 10")
//    @field:Max(100_000, message = "Максимальный ресурс на раунд — 100000")
//    val roundSum: Int,
//
//    @field:NotNull(message = "timeoutMoveSec — это обязательное поле")
//    @field:Min(10, message = "Минимальное время на ход — 10 секунд")
//    @field:Max(300, message = "Максимальное время на ход — 300 секунд")
//    val timeoutMoveSec: Int,
//)
