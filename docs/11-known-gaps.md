# 11. Что не реализовано, TODO, риски

Живой список — обновлять при каждом изменении, влияющем на пункты ниже.

## Бизнес-логика

### Нет расчёта баллов/выплат
- `Offer`/`Decision` сохраняются, но **баланс игроков не считается** и не хранится.
- `StatsService.getSessionStats` собирает сырые данные (offer.amount, accepted), но никакой агрегации по игрокам/командам не делает.
- Клиент должен считать сам, либо это нужно допилить.

### Таймауты хода не работают
- `SessionConfig.timeoutMoveSec` есть в модели и валидируется (10..300), но **автоматических переходов по таймауту нет**.
- Раунд «зависает» в `WAIT_OFFERS` / `OFFERS_SENT`, пока не соберутся все оффера/решения.

### Заглушки в AdminGameplayService
- ~~`abortCurrentRound()`~~ — реализован в T-054 (WS endpoint `/app/session/{id}/round.abort`, phase→ABORTED).
- `pauseRound()` — удалён как dead code (не было endpoint'а). Заводить если реально нужно клиенту.
- Файл: `services/AdminGameplayService.kt`.

### Роль NPC
- Значение в enum есть (`model/Role.kt`), но код для NPC-игроков отсутствует. `AuthService.quickRegister` явно запрещает создание пользователя с этой ролью.

## Персистентность

### `index.sql` не применяется автоматически
- Файл `src/main/resources/index.sql` — не запускается через Hibernate. Нужно либо вручную, либо добавить Flyway/Liquibase, либо `@EventListener` на `ApplicationReadyEvent`.
- Без этого `SessionRepository.searchByNameTrgm` будет работать, но без ускорения индексом.

### `ddl-auto=update`
- Автомиграция удобна для dev, но опасна для prod (не удаляет колонки, ловит edge-cases при переименованиях). Для prod — Flyway/Liquibase.

### ~~Потенциальный N+1~~ — устранено (T-002)
- `DecisionRepository.findAllBySessionIdWithRelations` теперь с fetch join по `offer`/`responder`/`round` (по аналогии с `OfferRepository`).

### FK-индексы
- Явно определён только GIN-индекс `idx_session_name_trgm`. Остальные индексы на FK создаются Hibernate по дефолту, но конкретно проверить в проде стоит.

## Безопасность

### Impersonation принята как допустимый риск
- Пет-проект, T-010. Аутентификация — только Bearer JWT (RFC 6750); anti-impersonation отсутствует **by design**.
- Любой залогиненный пользователь может, зная чужой `sessionId` / `userId`, слать SEND и подписываться на топики от имени других — сервер отличает лишь того, кто пришёл с валидным JWT.
- Для prod — вернуть session-membership + персональные проверки (см. историю T-010).

### JWT TTL 365 дней
- `services/JwtService.kt:85` — токен живёт год. При компрометации отозвать невозможно (нет revocation list, нет refresh-токенов).
- Рекомендация: короткий access + refresh (15–30 мин + 7–30 дней).

### CORS слишком широкий для prod
- HTTP: `allowedOriginPatterns = ["http://localhost:[*]"]` — dev-only.
- STOMP handshake: `setAllowedOrigins("*")` (`WebSocketConfig.kt:51`) — слишком широко.
- Перед деплоем в prod — сузить до конкретных доменов.

### Actuator полностью открыт
- `management.endpoints.web.exposure.include=*` (`application.properties:3`).
- `/actuator/**` в `permitAll` (`SecurityConfiguration.kt`). Наружу могут утекать `/env`, `/beans`, `/heapdump`. Ограничить до `health`, `info` для publish + auth для остального.

### DEBUG/TRACE логирование
- `logging.level.org.springframework.security=DEBUG`
- STOMP handlers на `TRACE`.
- В логах могут оказаться токены, тела запросов. Для prod — переключить на `INFO`.

### Отсутствие проверки `Session.state` при действиях STOMP
- После удаления `PlaySessionStompChannelInterceptor` вообще не проверяется, что сессия активна: игрок может слать `offer.create` в `FINISHED`/`ABORTED` сессию — упадёт глубже в бизнес-логике на NPE (`session.currentRound!!`), что даст 500.
- Проверять `state` в бизнес-логике (сервисах), а не в интерцепторе.

## Тестовое покрытие

- Unit-тесты: покрытие бизнес-логики (`services/*` + `model.ShuffleStrategy`) ≥80% line, гейт `./gradlew check` через JaCoCo (T-012, порог зафиксирован в `build.gradle.kts`).
- Нет интеграционных тестов на: REST endpoints, WebSocket flow, JWT-auth end-to-end, race conditions на «последний оффер / последнее решение». Это отдельный слой поверх юнит-тестов.

## Прочее

### Оставшиеся имена и опечатки
- В DTO встречается «Prew» (вместо «Preview»): `OfferPrewResponse`, `DecisionPrewResponse`, `RoundPrewResponse` и т. д. Формально работает, но при рефакторинге стоит переименовать.

### PlantUML/старые диаграммы
- `src/main/resources/doc/old-er-diagram.md` и `.puml` могут расходиться с текущей моделью. Верить коду, диаграммы обновлять при рефакторинге.

## Как это использовать

При работе над фичей, попадающей в один из пунктов выше:
1. Пометить связанный пункт как «в работе» / удалить, если закрыто.
2. Обновить соответствующие `docs/` (см. `01-overview.md` таблицу «Точки входа»).
3. Если появляется новая пропасть — добавить сюда.
