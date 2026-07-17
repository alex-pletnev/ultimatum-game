# 04. REST API

**Base URL:** `http://localhost:8080/api/v1`

Все защищённые endpoint'ы требуют заголовок:
```
Authorization: Bearer <accessToken>
```

Автогенерируемая полная спека: [specs/openapi.json](specs/openapi.json). Интерактивно: Swagger UI на `http://localhost:8080/api/v1/swagger-ui.html`.

## Сводка

| Метод | Путь | Роль | Кратко |
|-------|------|------|--------|
| POST | `/auth/quick-register` | permitAll | Регистрация нового пользователя |
| POST | `/auth/quick-login` | permitAll | Вход по существующему `userId` |
| POST | `/auth/refresh` | permitAll | Обмен refresh → новый access |
| POST | `/auth/logout` | authenticated | Отзыв текущего access-токена |
| GET | `/user` | authenticated | Профиль текущего пользователя |
| GET | `/user/id` | authenticated | Только id текущего пользователя |
| POST | `/session` | ADMIN | Создать сессию |
| GET | `/session` | any auth | Список сессий (пагинация + фильтры) |
| GET | `/session/{id}` | any auth | Детали сессии |
| GET | `/session/{id}/with-teams-and-members` | permitAll | Сессия + команды + участники + наблюдатели (публично — без JWT) |
| GET | `/session/{id}/current-round` | any auth | Текущий раунд сессии |
| GET | `/session/{id}/rounds` | any auth | История всех раундов + офферы + решения |
| POST | `/session/{sessionId}/join` | ADMIN, PLAYER | Присоединиться как игрок |
| POST | `/session/{sessionId}/join/observer` | any auth | Присоединиться как наблюдатель |
| GET | `/statistics/{sessionId}/csv` | permitAll | Экспорт статистики сессии в CSV (публично — без JWT) |
| POST | `/npc` | ADMIN | Создать NPC-профиль (см. [10-npc.md](10-npc.md)) |
| GET | `/npc` | ADMIN | Список NPC-профилей |
| GET | `/npc/{id}` | ADMIN | Один NPC-профиль |
| DELETE | `/npc/{id}` | ADMIN | Удалить NPC |
| POST | `/session/{sessionId}/join-npc` | ADMIN | Приаттачить существующего NPC к сессии |
| POST | `/session/{sessionId}/npcs` | ADMIN | Bulk: создать N NPC + сразу приаттачить |

---

## Auth

Все — в [03-auth.md](03-auth.md).

---

## Session

### `POST /session` — создать сессию

**Роль:** ADMIN.

**Body:**
```json
{
  "displayName": "My session",       // 3..100, required
  "state": "CREATED",                 // default; можно опустить
  "openToConnect": true,              // default true
  "config": {
    "sessionType": "FREE_FOR_ALL",   // "FREE_FOR_ALL" | "TEAM_BATTLE"
    "numRounds": 3,                   // 1..10
    "numTeams": 0,                    // 0 для FREE_FOR_ALL, 2..5 для TEAM_BATTLE
    "numPlayers": 4,                  // 2..120
    "roundSum": 100,                  // 10..100000
    "timeoutMoveSec": 60              // 10..300 (в UI сервер таймер не enforce'ит)
  }
}
```

**201 Created:** `SessionWithTeamsAndMembersResponse` (см. [06-data-models.md](06-data-models.md)).

**Побочные эффекты:** создаётся сессия с N пустыми раундами и M пустыми командами (для TEAM_BATTLE). Текущий пользователь становится `admin` сессии.

**Ошибки:**
- `400` — валидация не прошла (см. диапазоны выше).
- `403` — не ADMIN.

### `GET /session` — список сессий

Пагинация + фильтры.

**Query params (все optional):**

| Параметр | Тип | Default | Комментарий |
|----------|-----|---------|-------------|
| `s` | string, ≤100 | `""` | Поиск по displayName (ранжирование по pg_trgm если нет других фильтров, иначе ILIKE) |
| `page` | int | 0 | Номер страницы (0-based) |
| `pageSize` | int | 30 | Размер страницы |
| `state` | string | — | Фильтр: `CREATED` \| `RUNNING` \| `FINISHED` \| `ABORTED` |
| `sessionType` | string | — | Фильтр: `FREE_FOR_ALL` \| `TEAM_BATTLE` |
| `openToConnect` | boolean | — | Фильтр по возможности присоединения |

Фильтры комбинируются AND.

**200 OK:** `Page<SessionResponse>` — стандартный Spring Data page:
```json
{
  "content": [ /* SessionResponse[] */ ],
  "totalElements": 42,
  "totalPages": 2,
  "size": 30,
  "number": 0,
  "first": true,
  "last": false,
  "empty": false,
  "numberOfElements": 30
}
```

**Пример: «сессии, куда можно присоединиться»:**
```
GET /api/v1/session?state=CREATED&openToConnect=true
```

### `GET /session/{id}` — детали сессии

**200 OK:** `SessionResponse` (полный). См. [06-data-models.md](06-data-models.md).

**Ошибки:** `400` (не UUID), `404` (не найдена).

### `GET /session/{id}/with-teams-and-members`

Расширенная версия — с раскрытыми `teams` (внутри `members`), плюс `members` и `observers` на верхнем уровне.

**Роль:** publicly accessible (без JWT). Публичный артефакт — ник-состав + roundSum. POST-мутации остались с auth.

**200 OK:** `SessionWithTeamsAndMembersResponse`.

Используется когда фронту нужно показать «кто уже в сессии» и/или «какие команды с их составом», в том числе на публичной странице статистики завершённой партии.

### `GET /session/{id}/current-round`

**200 OK:** `RoundResponse` — текущий раунд сессии со всеми офферами и решениями. Плюс per-user hints:
- `myRole`: `PROPOSER` \| `RESPONDER` \| `BOTH` \| `NONE` — моя роль в этом раунде.
- `myPendingActions`: массив действий, которые ещё осталось сделать (`SEND_OFFER` в фазе WAIT_OFFERS; `MAKE_DECISION` с `offerId` в фазе OFFERS_SENT).

**Ошибки:** `404` — если у сессии нет `currentRound` (не стартована / уже завершена).

### `GET /session/{id}/rounds`

История всех раундов сессии.

**200 OK:** `List<RoundResponse>` — отсортировано по `roundNumber`. Внутри каждого — все `offers` и `decisions`. Аналогичные `myRole`/`myPendingActions` per-round.

**Ошибки:** `404` — сессия не найдена.

### `POST /session/{sessionId}/join` — присоединиться игроком

**Роль:** ADMIN или PLAYER.

**Query params:**
- `teamId: UUID?` — обязателен для TEAM_BATTLE, игнорируется для FREE_FOR_ALL.

**200 OK:** `SessionWithTeamsAndMembersResponse` (обновлённая).

**Ошибки:**
- `409` — сессия закрыта (`openToConnect=false`) / уже полна (`members.size >= numPlayers`) / вы админ этой сессии.

**Auto-close полных сессий (T-093):** любой успешный `join` / `join-npc` /
`bulk-npcs`, который заполнил `members.size` до `config.numPlayers`, автоматически
переводит сессию в `openToConnect=false` и публикует `sessionStatus`. Значит
после этого фильтр `GET /session?openToConnect=true` полную сессию уже не отдаст.
Используй `SessionResponse.membersCount` — не считай места вручную по `teams`.

### `POST /session/{sessionId}/join/observer`

**Роль:** ADMIN, PLAYER, OBSERVER.

Присоединяет как `observer`. Если пользователь был в `members` — удалит оттуда (переход из активного игрока в наблюдателя).

**200 OK:** `SessionWithTeamsAndMembersResponse`.

**Ошибки:** `409` если пользователь — admin сессии.

---

## Statistics

### `GET /statistics/{sessionId}/csv` — CSV-экспорт

**Роль:** publicly accessible (без JWT). Летопись партии — публичный артефакт, ссылку можно кидать в чат/форум без принуждения открывателя регистрироваться.

**Response:**
- `Content-Type: text/plain` (не `text/csv` для универсальной совместимости).
- `Content-Disposition: attachment; filename="session-{sessionId}-stats.csv"`.
- Тело — CSV в UTF-8:
```
offerId,roundNumber,amount,proposerId,proposerNickname,responderId,responderNickname,proposerTeamId,proposerTeamName,responderTeamId,responderTeamName,accepted,timestamp
```

**Ошибки:** `404` — сессия не найдена.

---

## Формат ошибок

Все ошибки — единый JSON `ApiErrorResponse`:

```json
{
  "timestamp": "2026-07-15T09:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "displayName: must not be blank",
  "path": "/api/v1/session"
}
```

Полная таблица кодов — [07-error-handling.md](07-error-handling.md).
