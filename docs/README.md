# Ultimatum Game — Документация

Набор справочных файлов для AI-агентов и разработчиков. Каждый файл — плотный, ориентированный на быстрый поиск: таблицы, ссылки `file:line`, минимум прозы.

## Индекс

| № | Файл | Тема |
|---|------|------|
| 01 | [overview.md](01-overview.md) | Что это, стек, запуск, ключевые концепции |
| 02 | [domain-model.md](02-domain-model.md) | Сущности, JPA-связи, enum'ы, ER, индексы |
| 03 | [state-machines.md](03-state-machines.md) | Переходы `SessionState` и `RoundPhase` |
| 04 | [services.md](04-services.md) | Сервисы: методы, сигнатуры, побочные эффекты |
| 05 | [rest-api.md](05-rest-api.md) | REST endpoints |
| 06 | [websocket-api.md](06-websocket-api.md) | STOMP: SEND, topics, авторизация |
| 07 | [dto-and-mappers.md](07-dto-and-mappers.md) | DTO с валидациями, MapStruct-мапперы |
| 08 | [security.md](08-security.md) | JWT (RFC 6750 Bearer), CORS, STOMP-интерцептор |
| 09 | [error-handling.md](09-error-handling.md) | Исключения → HTTP коды |
| 10 | [configuration.md](10-configuration.md) | properties, env, compose, gradle |
| 11 | [known-gaps.md](11-known-gaps.md) | Что не реализовано, TODO, security-concerns |

## Как читать

- **Быстрый старт** → `01-overview.md`.
- **Пишешь фичу в бизнес-логике** → `03` + `04`.
- **Интегрируешься с API** → `05` + `06` + `07`.
- **Трогаешь auth/permissions** → `08`.
- **Меняешь модель/схему БД** → `02` + `03`.
- **Планируешь следующие фичи** → `11`.

## Соглашения

- Пути в проекте — относительные от корня `ultimatum-game/`.
- Все ссылки `file:line` — актуальны на момент создания документа. При расхождении с кодом — верить коду.
- Пакет базовый: `edu.itmo.ultimatum_game`.
