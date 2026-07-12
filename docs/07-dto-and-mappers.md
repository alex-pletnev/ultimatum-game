# 07. DTO и MapStruct-мапперы

DTO живут в `dto/requests/` и `dto/responses/`. Мапперы в `util/`. Все мапперы:

```kotlin
@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING
)
```

Регистрируются автоматически как Spring beans.

---

## Request DTO

### CreateUserRequest — `dto/requests/UserRequests.kt:9`
```kotlin
data class CreateUserRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 42)
    val nickname: String?,
    val role: Role = Role.PLAYER,
)
```

### AuthenticateUserRequestDto — `dto/requests/UserRequests.kt:16`
```kotlin
data class AuthenticateUserRequestDto(
    @field:NotBlank
    val id: String? = "",
)
```

### CreateSessionRequest — `dto/requests/CreateSessionRequest.kt:14`
```kotlin
data class CreateSessionRequest(
    val id: UUID? = null,
    @field:Size(min = 3, max = 100)
    @field:NotBlank
    val displayName: String? = null,
    val state: SessionState = SessionState.CREATED,
    @field:Valid
    @field:NotNull
    val config: SessionConfigDto? = null,
    val openToConnect: Boolean = true,
) : Serializable
```

### SessionConfigDto — `dto/requests/SessionConfigDto.kt:14`
```kotlin
data class SessionConfigDto(
    val sessionType: SessionType = SessionType.FREE_FOR_ALL,
    @field:Min(1) @field:Max(10) @field:Positive
    val numRounds: Int? = null,
    @field:Max(5) @field:PositiveOrZero
    val numTeams: Int = 0,
    @field:Range(min = 2, max = 120)
    val numPlayers: Int? = null,
    @field:Range(min = 10, max = 100000)
    val roundSum: Int? = null,
    @field:Range(min = 10, max = 300)
    val timeoutMoveSec: Int? = null,
) : Serializable
```

### CreateOfferCmd — `dto/requests/CreateOfferCmd.kt:9`
```kotlin
data class CreateOfferCmd(
    @field:PositiveOrZero
    val amount: Int? = null,
) : Serializable
```

### MakeDecisionCmd — `dto/requests/MakeDecisionCmd.kt:6`
```kotlin
data class MakeDecisionCmd(
    @field:NotBlank
    val offerId: String = "",
    val decision: Boolean = true,
) : Serializable
```

---

## Response DTO

### JwtAuthenticationResponse — `dto/responses/JwtAuthenticationResponse.kt:5`
`{ token: String }`

### CsrfTokenResponse — `dto/responses/CsrfTokenResponse.kt:5`
`{ token, headerName, parameterName: String }`

### UserResponse — `dto/responses/UserResponse.kt:10`
`{ id: UUID, nickname: String, role: Role, createdAt: Date }`

### UserIdResponse — `dto/responses/UserIdResponse.kt:1`
`{ id: UUID }`

### SessionConfigResponse — `dto/responses/SessionConfigResponse.kt:9`
`{ sessionType, numRounds, numTeams, numPlayers, roundSum, timeoutMoveSec }`

### SessionResponse — `dto/responses/SessionResponse.kt:10`
```
{
  id, displayName, state, createdAt,
  admin: UserResponse,
  openToConnect,
  rounds: Set<RoundPrewResponse>,
  config: SessionConfigResponse,
  teams: Set<TeamPrewResponse>,
  currentRound: RoundPrewResponse?
}
```

### SessionWithTeamsAndMembersResponse — `dto/responses/SessionWithTeamsAndMembersResponse.kt:10`
```
{
  id, displayName, state, createdAt,
  admin: UserResponse,
  openToConnect,
  currentRound: RoundPrewResponse?,
  config: SessionConfigResponse,
  teams: Set<TeamResponse>,        // с раскрытыми members
  members: Set<UserResponse>,
  observers: Set<UserResponse>
}
```

### SessionPrewResponse — `dto/responses/SessionPrewResponse.kt:10`
Облегчённая сессия для вложений: `{ id, displayName, state, createdAt, admin, openToConnect, config }` (без rounds/teams/members).

### RoundResponse — `dto/responses/RoundResponse.kt:10`
`{ id, roundNumber, roundPhase, offers: List<OfferPrewResponse>, decisions: List<DecisionPrewResponse>, session: SessionPrewResponse }`

### RoundPrewResponse — `dto/responses/RoundPrewResponse.kt:10`
`{ id, roundNumber, roundPhase }`

### OfferCreatedResponse — `dto/responses/OfferCreatedResponse.kt:9`
`{ id, round: RoundPrewResponse, proposer: UserResponse, responder: UserResponse?, offerValue: Int, createdAt }`

### OfferPrewResponse — `dto/responses/OfferPrewResponse.kt:9`
`{ id?, proposer?, responder?, offerValue?, createdAt }` (все поля nullable — для вложенности)

### DecisionMadeResponse — `dto/responses/DecisionMadeResponse.kt:9`
`{ id, round, responder, offer: OfferCreatedResponse, decision: Boolean, createdAt }`

### DecisionPrewResponse — `dto/responses/DecisionPrewResponse.kt:9`
`{ id?, responder?, offer?, decision?, createdAt }`

### TeamResponse — `dto/responses/TeamResponse.kt:9`
`{ id, name, members: Set<UserResponse> }`

### TeamPrewResponse — `dto/responses/TeamPrewResponse.kt:9`
`{ id, name }`

### StatsDtos — `dto/responses/StatsDtos.kt:6`
```kotlin
data class SessionStatsDto(
    val sessionId: UUID,
    val displayName: String,
    val state: SessionState,
    val createdAt: Date,
    val totalRounds: Int,
    val decisionsCount: Int,
    val offers: List<OfferStatsDto>,
)

data class OfferStatsDto(
    val offerId: UUID,
    val amount: Int,
    val proposer: UserInfo,
    val responder: UserInfo?,
    val proposerTeam: TeamInfo? = null,
    val responderTeam: TeamInfo? = null,
    val accepted: Boolean?,
    val roundNumber: Int,
    val timestamp: Date,
)

data class UserInfo(val id: UUID, val nickname: String)
data class TeamInfo(val id: UUID, val name: String)
```

### ApiErrorResponse — `dto/responses/ApiErrorResponse.kt:6`
`{ timestamp: Date, status: Int, error: String, message: String, path: String }`

---

## MapStruct-мапперы

Пакет `util/`.

| Маппер | Файл | Entity ↔ DTO | Зависит от |
|--------|------|--------------|------------|
| `UserMapper` | `UserMapper.kt:8-16` | `User` ↔ `UserResponse` | — |
| `SessionMapper` | `SessionMapper.kt:8-20` | `Session` ↔ `SessionResponse` / `CreateSessionRequest` | `SessionConfigMapper`, `RoundPrewMapper`, `UserMapper`, `TeamPrewMapper` |
| `SessionConfigMapper` | `SessionConfigMapper.kt:8-20` | `SessionConfig` ↔ `SessionConfigResponse` / `SessionConfigDto` | — |
| `SessionPrewMapper` | `SessionPrewMapper.kt:9-16` | `Session` → `SessionPrewResponse` | `SessionConfigMapper`, `UserMapper` |
| `SessionWithTeamsAndMembersMapper` | `SessionWithTeamsAndMembersMapper.kt:10-17` | `Session` → `SessionWithTeamsAndMembersResponse` | `SessionConfigMapper`, `RoundPrewMapper`, `UserMapper`, `TeamMapper` |
| `RoundMapper` | `RoundMapper.kt:7-25` | `Round` ↔ `RoundResponse`. `@AfterMapping` восстанавливает back-refs `Offer.round`, `Decision.round` | `OfferPrewMapper`, `DecisionPrewMapper`, `SessionPrewMapper` |
| `RoundPrewMapper` | `RoundPrewMapper.kt:8-16` | `Round` ↔ `RoundPrewResponse` | — |
| `OfferMapper` | `OfferMapper.kt:8-21` | `Offer` ↔ `OfferCreatedResponse` / `CreateOfferCmd`. **Ремап:** `CreateOfferCmd.amount → Offer.offerValue` | `RoundPrewMapper`, `UserMapper` |
| `OfferPrewMapper` | `OfferPrewMapper.kt:7-17` | `Offer` → `OfferPrewResponse` | `UserMapper` (×2 для proposer/responder) |
| `DecisionMapper` | `DecisionMapper.kt:7-19` | `Decision` ↔ `DecisionMadeResponse` | `RoundPrewMapper`, `UserMapper`, `OfferMapper` |
| `DecisionPrewMapper` | `DecisionPrewMapper.kt:7-19` | `Decision` ↔ `DecisionPrewResponse` | `UserMapper`, `OfferPrewMapper` |
| `TeamMapper` | `TeamMapper.kt:7-19` | `Team` ↔ `TeamResponse` | `UserMapper` |
| `TeamPrewMapper` | `TeamPrewMapper.kt:9-13` | `Team` → `TeamPrewResponse` | — |
| `FieldMappers` | `FieldMappers.kt` | вспомогательные helper-функции для нетривиальных полей | — |

### Граф зависимостей

```
UserMapper
  ├─→ TeamMapper ──┐
  ├─→ TeamPrewMapper
  ├─→ OfferMapper ─→ DecisionMapper
  ├─→ OfferPrewMapper ─→ DecisionPrewMapper
  ├─→ SessionConfigMapper (нет зависимостей)
  ├─→ SessionPrewMapper ─┐
  │                       ├─→ RoundMapper (+ RoundPrewMapper)
  │                       │
  ├─→ SessionMapper
  └─→ SessionWithTeamsAndMembersMapper
```

## Правила именования

- `*Response` — полные DTO для эндпоинтов чтения.
- `*PrewResponse` — «превью», используется для вложений (без циклов), поля обычно nullable.
- `*Request` — входные для REST-контроллеров.
- `*Cmd` — входные для WebSocket-контроллеров (командный формат).
- `*Dto` — используется для внутренних DTO (`SessionConfigDto`, `SessionStatsDto`).

## Ремапы (source → target)

- `CreateOfferCmd.amount` → `Offer.offerValue` — `OfferMapper.kt`.
- Остальные поля мапятся 1-в-1 по имени.

## См. также

- `docs/05-rest-api.md` — какие DTO использует каждый REST endpoint.
- `docs/06-websocket-api.md` — DTO для WS-payloads.
