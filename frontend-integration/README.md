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

## Спецификации

- [specs/openapi.json](specs/openapi.json) — OpenAPI 3.0, сгенерирован из кода. Годится для кодогенерации типов/клиента (`openapi-typescript-codegen`, `orval`, и т.д.).
- [specs/asyncapi.json](specs/asyncapi.json) — AsyncAPI 3.0 для WebSocket/STOMP-каналов.

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
