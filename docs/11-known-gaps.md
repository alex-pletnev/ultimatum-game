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
- `abortCurrentRound()` — не реализован.
- `pauseRound()` — TODO / bonus, не реализован.
- Файл: `services/AdminGameplayService.kt`.

### Роль NPC
- Значение в enum есть (`model/Role.kt`), но код для NPC-игроков отсутствует. `AuthService.quickRegister` явно запрещает создание пользователя с этой ролью.

## Персистентность

### `index.sql` не применяется автоматически
- Файл `src/main/resources/index.sql` — не запускается через Hibernate. Нужно либо вручную, либо добавить Flyway/Liquibase, либо `@EventListener` на `ApplicationReadyEvent`.
- Без этого `SessionRepository.searchByNameTrgm` будет работать, но без ускорения индексом.

### `ddl-auto=update`
- Автомиграция удобна для dev, но опасна для prod (не удаляет колонки, ловит edge-cases при переименованиях). Для prod — Flyway/Liquibase.

### Потенциальный N+1
- `DecisionRepository.findBySessionId` — без fetch join. При больших сессиях (много игроков × раундов) приведёт к N+1 при обращении к `decision.offer`, `decision.responder`, `decision.round`.
- Рассмотреть fetch join по аналогии с `OfferRepository.findAllBySessionIdWithRelations`.

### FK-индексы
- Явно определён только GIN-индекс `idx_session_name_trgm`. Остальные индексы на FK создаются Hibernate по дефолту, но конкретно проверить в проде стоит.

## Безопасность

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

### CSRF на всех endpoints
- Включён глобально. Мobile/SPA клиентам нужно предварительно ходить на `GET /csrf` и хранить токен. Учесть в клиентской документации.

### Проверка `PlaySessionStompChannelInterceptor`
- Проверяет только принадлежность к сессии, но не её `state`. Игрок может отправить оффер в `FINISHED`/`ABORTED` сессию — упадёт глубже в бизнес-логике на NPE (`session.currentRound!!`), что даст 500. Стоит проверять и `state` в интерцепторе или бизнес-логике.

## Тестовое покрытие

- `UltimatumGameApplicationTests` — только `contextLoads`.
- `FreeForAllTest` — юнит на стратегию.
- Нет интеграционных тестов на: REST endpoints, WebSocket flow, JWT-auth, race conditions на «последний оффер / последнее решение».

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
