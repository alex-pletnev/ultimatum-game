# 05. REST API

**Base URL:** `/api/v1` (context path, см. `application.properties:2`).

Все защищённые endpoints требуют заголовок `Authorization: Bearer <JWT>` (RFC 6750). Никакого CSRF-заголовка не требуется (см. `docs/08-security.md`).

## Сводная таблица

| Метод | Путь | Роли | Файл |
|-------|------|------|------|
| POST | `/auth/quick-register` | PermitAll | `AuthController.kt:26-34` |
| POST | `/auth/quick-login` | PermitAll | `AuthController.kt:36-44` |
| POST | `/session` | ADMIN | `SessionController.kt:28-36` |
| GET | `/session` | ADMIN, PLAYER, OBSERVER | `SessionController.kt:65-79` |
| GET | `/session/{id}` | ADMIN, PLAYER, OBSERVER | `SessionController.kt:38-45` |
| GET | `/session/{id}/with-teams-and-members` | ADMIN, PLAYER, OBSERVER | `SessionController.kt:47-54` |
| GET | `/session/{id}/current-round` | ADMIN, PLAYER, OBSERVER | `SessionController.kt:56-63` |
| GET | `/session/{id}/rounds` | ADMIN, PLAYER, OBSERVER | `SessionController.kt:74-81` |
| POST | `/session/{sessionId}/join` | ADMIN, PLAYER | `SessionController.kt:81-92` |
| POST | `/session/{sessionId}/join/observer` | ADMIN, PLAYER, OBSERVER | `SessionController.kt:94-101` |
| GET | `/user` | authenticated | `UserController.kt:35-41` |
| GET | `/user/id` | authenticated | `UserController.kt:23-33` |
| GET | `/statistics/{sessionId}/csv` | ADMIN, PLAYER, OBSERVER | `StatisticController.kt:29-45` |

---

## Auth

### `POST /auth/quick-register`
Регистрация нового пользователя без пароля.

- **Body:** `CreateUserRequest` (см. `docs/07-dto-and-mappers.md`)
  - `nickname: String` — 3..42, required
  - `role: Role` — default `PLAYER`; `NPC` запрещена
- **Response 201:** `JwtAuthenticationResponse` `{ token: String }`
- **Ошибки:**
  - 400 `MethodArgumentNotValidException` — валидация nickname/role
  - 403 `UserRoleNotAllowedException` — `role = NPC`

### `POST /auth/quick-login`
Вход по существующему `userId`.

- **Body:** `AuthenticateUserRequestDto` `{ id: String (UUID) }`
- **Response 200:** `JwtAuthenticationResponse`
- **Ошибки:**
  - 400 `InvalidUuidFormatException`
  - 404 `IdNotFoundException`

---

## Session

### `POST /session` — создание сессии
- **Роль:** ADMIN.
- **Body:** `CreateSessionRequest`
  - `displayName: String` — 3..100, required
  - `state: SessionState` — default `CREATED`
  - `config: SessionConfigDto` — required, `@Valid`
    - `sessionType: SessionType` (`FREE_FOR_ALL` / `TEAM_BATTLE`)
    - `numRounds: Int` — 1..10
    - `numTeams: Int` — 0..5 (0 для FREE_FOR_ALL, ≥2 для TEAM_BATTLE)
    - `numPlayers: Int` — 2..120
    - `roundSum: Int` — 10..100000
    - `timeoutMoveSec: Int` — 10..300
  - `openToConnect: Boolean` — default `true`
- **Response 201:** `SessionWithTeamsAndMembersResponse`
- **Побочные эффекты:** создаётся Session с N раундами и M командами. Текущий пользователь становится `admin`.

### `GET /session` — список сессий
- **Query:**
  - `s: String` — фильтр по названию (≤100 символов, default `""`); pg_trgm ranking
  - `page: Int` — default `0`
  - `pageSize: Int` — default `30`
- **Response 200:** `Page<SessionResponse>`

### `GET /session/{id}` — детали сессии
- **Path:** `id: String (UUID)`
- **Response 200:** `SessionResponse`
- **Ошибки:** 400 `InvalidUuidFormatException`; 404 `IdNotFoundException`.

### `GET /session/{id}/with-teams-and-members`
Расширенная версия с командами (`teams`), игроками (`members`) и наблюдателями (`observers`).

- **Response 200:** `SessionWithTeamsAndMembersResponse`

### `GET /session/{id}/current-round`
- **Response 200:** `RoundResponse`
- **Ошибки:** 404 `IdNotFoundException` — если `session.currentRound == null`.

### `GET /session/{id}/rounds` — история всех раундов сессии
- **Response 200:** `List<RoundResponse>` — отсортировано по `roundNumber`, включает `offers` и `decisions` каждого раунда.
- **Ошибки:** 404 `IdNotFoundException` — если сессии нет.
- Транзакционный `@Transactional(readOnly = true)` — избегает LazyInitializationException при обходе `session.rounds`.

### `POST /session/{sessionId}/join` — присоединиться игроком
- **Роль:** ADMIN, PLAYER.
- **Path:** `sessionId: String (UUID)`
- **Query:** `teamId: String? (UUID)` — обязателен для TEAM_BATTLE, для FREE_FOR_ALL игнорируется.
- **Response 200:** `SessionWithTeamsAndMembersResponse`
- **Ошибки:** 409 `SessionJoinRejectedException` — закрыто / переполнено / admin пытается джойниться.

### `POST /session/{sessionId}/join/observer` — присоединиться наблюдателем
- **Роль:** ADMIN, PLAYER, OBSERVER.
- **Response 200:** `SessionWithTeamsAndMembersResponse`
- Удаляет пользователя из `members` если был.
- **Ошибки:** 409 `SessionJoinRejectedException` — если `user == admin`.

---

## User

### `GET /user` — профиль текущего пользователя
- **Роль:** любая аутентифицированная.
- **Response 200:** `UserResponse` `{ id, nickname, role, createdAt }`

### `GET /user/id` — только ID текущего пользователя
- **Response 200:** `UserIdResponse` `{ id: UUID }`

---

## Statistics

### `GET /statistics/{sessionId}/csv`
Экспорт статистики сессии в CSV.

- **Роль:** ADMIN, PLAYER, OBSERVER.
- **Path:** `sessionId: UUID`
- **Response 200:** `text/plain` (не `text/csv` — для универсальной совместимости), `Content-Disposition: attachment; filename="session-{sessionId}-stats.csv"`, тело — CSV UTF-8.
- **Ошибки:** 404 `EntityNotFoundException`.

Колонки CSV:
```
offerId, roundNumber, amount,
proposerId, proposerNickname,
responderId, responderNickname,
proposerTeamId, proposerTeamName,
responderTeamId, responderTeamName,
accepted, timestamp
```

---

## Формат ошибок

Все ошибки возвращаются в формате `ApiErrorResponse`:
```json
{
  "timestamp": "2026-07-12T12:34:56Z",
  "status": 400,
  "error": "Bad Request",
  "message": "…",
  "path": "/api/v1/session"
}
```

Полная таблица кодов — `docs/09-error-handling.md`.

## OpenAPI

Полная спека: `src/main/resources/doc/openapi.json` (OpenAPI 3.0, автогенерация из кода через springdoc).
Локальный Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`.
Регенерация снапшота: `./gradlew generateApiSnapshots`.
