# Ultimatum Game — Frontend Integration

Полная документация для разработки фронта поверх этого бекенда. Всё, что нужно чтобы поднять бек локально, понять правила игры, вызвать REST и подписаться на WebSocket-события — здесь.

## Как читать

| # | Файл | Тема |
|---|------|------|
| 01 | [running-backend.md](01-running-backend.md) | Как запустить/перезапустить бекенд, URLs, health-check |
| 02 | [game-rules.md](02-game-rules.md) | Правила игры «Ультиматум» + наши адаптации |
| 03 | [auth.md](03-auth.md) | Регистрация, JWT (access + refresh), logout, роли |
| 04 | [rest-api.md](04-rest-api.md) | REST endpoints с примерами |
| 05 | [websocket-api.md](05-websocket-api.md) | STOMP over WebSocket: connect, subscribe, send |
| 06 | [data-models.md](06-data-models.md) | DTO — request/response |
| 07 | [error-handling.md](07-error-handling.md) | Коды ошибок для REST и WS |
| 08 | [state-machines.md](08-state-machines.md) | Жизненный цикл Session и Round |
| 09 | [integration-flows.md](09-integration-flows.md) | Сквозные сценарии: от создания сессии до финиша |
| 10 | [npc.md](10-npc.md) | NPC — боты со стратегиями, autoAdvanceRounds, all-NPC-симуляция |
| 11 | [prod-deployment.md](11-prod-deployment.md) | Prod URLs, env-vars, CORS, HTTPS, startup latency, GH Pages deploy |

## Спецификации

- [specs/openapi.json](specs/openapi.json) — OpenAPI 3.0, сгенерирован из кода. Годится для кодогенерации типов/клиента (`openapi-typescript-codegen`, `orval`, и т.д.).
- [specs/asyncapi.json](specs/asyncapi.json) — AsyncAPI 3.0 для WebSocket/STOMP-каналов.

Оба файла синхронизируются автоматически: `./gradlew generateApiSnapshots` регенерирует `src/main/resources/doc/*.json` и через `finalizedBy` копирует их сюда — вручную `cp` делать не нужно.

## TL;DR — с чего начать

1. Поднять бек: [01-running-backend.md](01-running-backend.md).
2. Проверить `http://localhost:8080/api/v1/actuator/health` → `{"status":"UP"}`.
3. Открыть Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`.
4. Тестовый флоу: `POST /auth/quick-register` → получить JWT → `POST /session` → `POST /session/{id}/join`.
5. Для интеграции с WebSocket — `05-websocket-api.md`.
6. Сквозной сценарий с ролями — `09-integration-flows.md`.

## Стек бекенда (справочно)

Kotlin 1.9 + Spring Boot 3.4 + PostgreSQL + STOMP-over-WebSocket. Никаких особых требований к фронту не накладывает — стандартный REST + STOMP-клиент (`@stomp/stompjs` для JS/TS).

## Обратная связь

Если что-то не покрыто, устарело или ведёт себя иначе — команда бекенда чинит по запросу. Прямые ссылки на код проекта в текстах приведены как `path/to/file.kt:line`.


## Сценарии и права T-108

Актуальные изменения к прежним примерам:

- `POST /session` доступен PLAYER и ADMIN. Новое поле `hostPlays` (по умолчанию `false`) включает ведущего в состав; `true` занимает одно место и для команд добавляет его в первую команду.
- Ведение определяется `session.admin.id`, а не глобальной ролью. Добавление ботов, реванш и команды STOMP `/start`, `/close`, `/open`, `/round.start`, `/round.abort` доступны только владельцу. Чужой ADMIN получает отказ.
- `POST /session/solo`: `{ "strategy": "FAIR", "sessionType": "FREE_FOR_ALL", "numRounds": 3 }`. Создаёт и запускает партию из игрока и одного бота. Поддерживаются все пять стратегий и 1–10 раундов; команды недопустимы.
- `POST /session/{id}/rematch`: новая CREATED-партия с прежней конфигурацией и участием ведущего. Участники и боты не копируются.
- `GET /session/mine?page=0&pageSize=12`: Page с партиями, созданными текущим пользователем либо содержащими его среди игроков/наблюдателей; новые первыми, максимум 50 за запрос.
- `DICTATOR` использует случайное распределение FREE_FOR_ALL. После распределения сервер автоматически создаёт положительные решения для всех адресатов и начисляет очки. Клиент не предлагает отказаться.
- Игровые изменения и вступление сериализованы блокировкой строки партии. Старт требует CREATED, минимум двух игроков и допустимого состава команд. Отправлять предложения могут только участники, отвечать — только адресат.

Временная серверная установка описана в [deploy/README.md](../deploy/README.md). Старая YC release-цепочка к ней не относится. Существующие данные из прежнего облака автоматически не переносятся.
