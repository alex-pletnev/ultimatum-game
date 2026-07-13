# 04. Сервисы

Все сервисы в пакете `services/`. Аннотация `@Service`, инъекция через конструктор.

## Карта сервисов

| Сервис | Файл | Роль |
|--------|------|------|
| `SessionService` | `services/SessionService.kt` | CRUD/поиск сессий, join |
| `PlayerGameplayService` | `services/PlayerGameplayService.kt` | sendOffer, makeDecision |
| `AdminGameplayService` | `services/AdminGameplayService.kt` | start/close/open/abort/round.start |
| `CoreGameplayService` | `services/CoreGameplayService.kt` | shuffle офферов между фазами |
| `EventPublisherService` | `services/EventPublisherService.kt` | публикация WS-событий |
| `AuthService` | `services/AuthService.kt` | quickRegister/quickLogin |
| `JwtService` | `services/JwtService.kt` | генерация/валидация JWT |
| `SecurityService` | `services/SecurityService.kt` | текущий userId из контекста |
| `UserService` | `services/UserService.kt` | CRUD пользователей |
| `StatsService` | `services/StatsService.kt` | сбор статистики сессии |
| `CsvService` | `services/CsvService.kt` | сериализация статистики в CSV |

---

## SessionService

`services/SessionService.kt`.

| Метод | Сигнатура | Побочные эффекты |
|-------|-----------|------------------|
| `createSession` | `(CreateSessionRequest) → SessionWithTeamsAndMembersResponse` | INSERT `session` + N `round` + M `team`; `admin = currentUser` |
| `setSessionState` | `(UUID, SessionState) → Unit` | UPDATE `session.state` |
| `setCurrentRound` | `(UUID, Round) → Unit` | UPDATE `session.current_round_id` |
| `getSession` | `(UUID) → SessionResponse` | read-only |
| `getSessionEntity` | `(UUID) → Session` | read-only, для внутреннего использования |
| `getSessionWithTeamsAndMembers` | `(UUID) → SessionWithTeamsAndMembersResponse` | read-only |
| `getCurrentRound` | `(UUID) → RoundResponse` | read-only; `IdNotFoundException` если нет активного раунда |
| `getAllSessions` | `(page: Int, pageSize: Int, s: String) → Page<SessionResponse>` | pg_trgm search + pagination |
| `joinSession` | `(sessionId: UUID, teamId: UUID?) → SessionWithTeamsAndMembersResponse` | `members.add`; для TEAM_BATTLE — `team.members.add`; publish `sessionStatus` |
| `joinSessionAsObserver` | `(sessionId: UUID) → SessionWithTeamsAndMembersResponse` | `observers.add`; удаляет из `members` если был; publish `sessionStatus` |

Проверки `joinSession`:
- уже в сессии → идемпотентный return.
- `openToConnect == false` → `SessionJoinRejectedException`.
- `members.size >= config.numPlayers` → `SessionJoinRejectedException`.
- `user == admin` → `SessionJoinRejectedException`.

Приватные утилиты:
- `createRounds(Session)` — заполняет `rounds` N объектами `Round(roundNumber=1..N, phase=CREATED)`.
- `createTeams(Session)` — для `TEAM_BATTLE` создаёт `numTeams` команд с именами «Команда №1..N»; для `FREE_FOR_ALL` — ничего.

---

## PlayerGameplayService

`services/PlayerGameplayService.kt`.

| Метод | Сигнатура | Побочные эффекты |
|-------|-----------|------------------|
| `sendOffer` | `(sessionId: UUID, playerId: UUID, CreateOfferCmd) → Unit` | INSERT `offer`; UPDATE `round.offers`; publish `offerCreated`. Если это последний оффер — переход `WAIT_OFFERS → ALL_OFFERS_RECEIVED → OFFERS_SENT`, вызов `CoreGameplayService.initWaitDecisionsPhase`, publish `roundStatus` + `offerToPlayer` каждому responder |
| `makeDecision` | `(sessionId: UUID, playerId: UUID, MakeDecisionCmd) → Unit` | INSERT `decision`; UPDATE `round.decisions`; publish `decisionMade`. Если это последнее решение — переход `OFFERS_SENT → ALL_DECISIONS_RECEIVED`, publish `roundStatus` |

Проверки:
- `sendOffer`: если пропонент уже отправил в этом раунде → `DuplicateIdException`.
- `makeDecision`: если респондент уже принимал решение в этом раунде → `DuplicateIdException`; оффер не найден → `IdNotFoundException`.

---

## AdminGameplayService

`services/AdminGameplayService.kt`.

| Метод | Сигнатура | Побочные эффекты |
|-------|-----------|------------------|
| `startSession` | `(sessionId: UUID) → Unit` | `state=RUNNING`, `currentRound=r1`, `r1.phase=WAIT_OFFERS`, `openToConnect=false`; publish `sessionStatus` |
| `closeSession` | `(sessionId: UUID) → Unit` | `openToConnect=false`; publish `sessionStatus` |
| `openSession` | `(sessionId: UUID) → Unit` | `openToConnect=true`; publish `sessionStatus` |
| `abortSession` | `(sessionId: UUID) → Unit` | `state=ABORTED`, `openToConnect=false`; publish `roundStatus` |
| `startNextRound` | `(sessionId: UUID) → Unit` | `currentRound.phase=FINISHED`. Если есть следующий: `nextRound.phase=WAIT_OFFERS`, `currentRound=nextRound`. Иначе: `state=FINISHED`; publish `roundStatus` |
| `abortCurrentRound` | — | **TODO**, не реализован |
| `pauseRound` | — | **TODO** |

---

## CoreGameplayService

`services/CoreGameplayService.kt`.

| Метод | Сигнатура | Побочные эффекты |
|-------|-----------|------------------|
| `initWaitDecisionsPhase` | `(Session) → Unit` | Вызов `session.config.sessionType.shuffleStrategy.shuffleOffers(session)` (мутирует `offer.responder`); `round.phase=OFFERS_SENT`; publish `publishOfferToPlayer` для каждого оффера |

---

## EventPublisherService

`services/EventPublisherService.kt`. Использует `SimpMessagingTemplate` (Lazy).

| Метод | Destination |
|-------|-------------|
| `publishOfferCreated(sessionId, offer)` | `/topic/session/{sessionId}/offerCreated` |
| `publishOfferToPlayer(sessionId, responderId, offer)` | `/topic/session/{sessionId}/player/{responderId}/offer` |
| `publishDecisionMade(sessionId, decision)` | `/topic/session/{sessionId}/decisionMade` |
| `publishRoundStatus(sessionId, round)` | `/topic/session/{sessionId}/roundStatus` |
| `publishSessionStatus(sessionId, session)` | `/topic/session/{sessionId}/sessionStatus` |

Payload — соответствующий DTO (см. `docs/07-dto-and-mappers.md`).

**Внимание** (T-017): собственно доменные события пишутся не здесь, а в вызывающих сервисах через `DomainEventLogger.emit(...)`. `EventPublisherService` отвечает только за фан-аут WS-сообщений. Разделение: WS-payload идёт по своему каналу для UI, а `DomainEvent` — по логам и Micrometer-counter'ам для мониторинга. См. `docs/12-observability.md`.

---

## DomainEventLogger

`util/DomainEventLogger.kt` (T-017). Единственный «жёсткий» канал для доменных событий: одна запись в структурированный лог + Micrometer counter. См. полный контракт и список событий — `docs/12-observability.md`.

---

## AuthService

`services/AuthService.kt`.

| Метод | Сигнатура | Побочные эффекты |
|-------|-----------|------------------|
| `quickLogin` | `(AuthenticateUserRequestDto) → JwtAuthenticationResponse` | Читает `User` по ID; `JwtService.generateToken` |
| `quickRegister` | `(CreateUserRequest) → JwtAuthenticationResponse` | INSERT `users`; `JwtService.generateToken`. `Role.NPC` запрещена → `UserRoleNotAllowedException` |

---

## JwtService

`services/JwtService.kt`. Алгоритм HS256, TTL 365 дней, ключ из `token.signing.key` (base64).

| Метод | Сигнатура | Комментарий |
|-------|-----------|-------------|
| `generateToken` | `(UserDetails) → String` | Claims: `sub=user.id`, `iat`, `exp`; extra: `nickname`, `role`, `createdAt` |
| `extractUsername` | `(String) → String` | Извлекает `sub` (UUID как строка) |
| `isTokenValid` | `(String, UserDetails) → Boolean` | Проверяет `!expired && sub == userDetails.username` |
| `extractExpiration` | `(String) → Date` | private |
| `isTokenExpired` | `(String) → Boolean` | private |

Подробнее — `docs/08-security.md`.

---

## SecurityService

`services/SecurityService.kt`.

| Метод | Сигнатура | Комментарий |
|-------|-----------|-------------|
| `getCurrentUserId` | `() → UUID` | Читает из `SecurityContextHolder`; `UUID.fromString(auth.name)` |

---

## UserService

`services/UserService.kt`.

| Метод | Сигнатура | Побочные эффекты |
|-------|-----------|------------------|
| `getUserById` | `(UUID) → User` | read; `IdNotFoundException` если нет |
| `save` | `(User) → User` | UPSERT |
| `create` | `(User) → User` | INSERT; `DuplicateIdException` если уже есть |
| `getUserDetailService` | `() → (UUID) → User` | для Spring Security |
| `getCurrentUser` | `() → User` | `SecurityService.getCurrentUserId` + `getUserById` |

---

## StatsService

`services/StatsService.kt`.

| Метод | Сигнатура | Побочные эффекты |
|-------|-----------|------------------|
| `getSessionStats` | `(UUID) → SessionStatsDto` | read-only агрегация; `EntityNotFoundException` если сессии нет |

Возвращает `SessionStatsDto` (см. `docs/07-dto-and-mappers.md`): все оффера/решения сессии с ссылками на пропонентов, респондентов и команды.

---

## CsvService

`services/CsvService.kt`. Использует `commons-csv`.

| Метод | Сигнатура | Комментарий |
|-------|-----------|-------------|
| `toCsv` | `(SessionStatsDto) → ByteArray` | UTF-8 CSV со столбцами: `offerId, roundNumber, amount, proposerId, proposerNickname, responderId, responderNickname, proposerTeamId, proposerTeamName, responderTeamId, responderTeamName, accepted, timestamp` |

---

## Граф вызовов ключевых сценариев

### Создание сессии
```
SessionController.createSession
  └─ SessionService.createSession
      ├─ SessionMapper.toEntity
      ├─ UserService.getCurrentUser
      ├─ createRounds (private)
      ├─ createTeams (private)
      └─ SessionRepository.save
```

### Отправка оффера (последний в раунде)
```
OfferWsController.createOffer
  └─ PlayerGameplayService.sendOffer
      ├─ UserService.getUserById
      ├─ SessionService.getSessionEntity
      ├─ OfferRepository.save
      ├─ RoundRepository.save
      ├─ EventPublisherService.publishOfferCreated
      ├─ [если последний] CoreGameplayService.initWaitDecisionsPhase
      │   ├─ session.config.sessionType.shuffleStrategy.shuffleOffers
      │   └─ EventPublisherService.publishOfferToPlayer (×N)
      ├─ SessionRepository.save
      └─ [если последний] EventPublisherService.publishRoundStatus
```

### Решение (последнее в раунде)
```
OfferWsController.makeDecision
  └─ PlayerGameplayService.makeDecision
      ├─ UserService.getUserById
      ├─ SessionService.getSessionEntity
      ├─ DecisionRepository.save
      ├─ RoundRepository.save
      ├─ EventPublisherService.publishDecisionMade
      ├─ SessionRepository.save
      └─ [если последнее] EventPublisherService.publishRoundStatus
```

### Экспорт статистики
```
StatisticController.exportCsv
  ├─ StatsService.getSessionStats
  │   └─ OfferRepository.findAllBySessionIdWithRelations
  │   └─ DecisionRepository.findAllBySessionIdWithRelations
  └─ CsvService.toCsv
```

## См. также

- `docs/03-state-machines.md` — какие переходы триггерят методы этих сервисов.
- `docs/06-websocket-api.md` — где эти сервисы вызываются из WS-контроллеров.
- `docs/11-known-gaps.md` — что ещё не реализовано (баллы, таймауты, `abortCurrentRound`).
