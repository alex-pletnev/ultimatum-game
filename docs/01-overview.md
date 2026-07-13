# 01. Обзор

## Что это

Сервер для проведения игры **«Ультиматум»** (классическая поведенческая экономика): один игрок предлагает разделение суммы, другой принимает или отклоняет. Отклонение — оба получают ноль.

Поддерживает 2 режима:
- **FREE_FOR_ALL** — каждый игрок в раунде предлагает случайному другому.
- **TEAM_BATTLE** — предлагающий получает респондента строго из другой команды.

## Стек

| Слой | Технология | Версия |
|------|-----------|--------|
| Язык | Kotlin | 1.9.25 |
| JVM | Java toolchain | 21 |
| Фреймворк | Spring Boot | 3.4.4 |
| Веб | spring-boot-starter-web (Tomcat) | 3.4.4 |
| WebSocket | spring-boot-starter-websocket (STOMP) | 3.4.4 |
| ORM | spring-boot-starter-data-jpa (Hibernate) | 3.4.4 |
| Безопасность | spring-boot-starter-security + spring-security-messaging | 3.4.4 / 6.1.0 |
| БД | PostgreSQL | latest (compose) |
| JWT | jjwt | 0.11.5 |
| DTO-мапперы | MapStruct + kapt | 1.6.3 |
| CSV | commons-csv | 1.11.0 |
| Валидация | spring-boot-starter-validation | 3.4.4 |
| Actuator | spring-boot-starter-actuator | 3.4.4 |
| OpenAPI | springdoc-openapi-starter-webmvc-ui | 2.2.0 |

Билд: Gradle Kotlin DSL. См. `build.gradle.kts:1-90`.

## Пакеты и структура

```
edu.itmo.ultimatumgame
├── UltimatumGameApplication.kt   # entry point
├── configs/                       # Security, WebSocket, интерцепторы
├── controllers/                   # REST + ws/ WebSocket
├── services/                      # Бизнес-логика
├── model/                         # JPA-сущности и enum'ы
├── repositories/                  # Spring Data JPA
├── dto/{requests,responses}/      # DTO
├── util/                          # MapStruct-мапперы, Logger
└── exceptions/                    # Кастомные исключения + GlobalExceptionsHandler
```

## Base URL и контекст

- HTTP context path: **`/api/v1`** (см. `application.properties:2`).
- STOMP endpoint: **`/api/v1/ws`** (`WebSocketConfig.kt:31`).
- STOMP prefixes: `/app` (клиент → сервер), `/topic` (broadcast).

## Ключевые концепции

- **Session** — игровая сессия с админом, конфигом, участниками (`members`), наблюдателями (`observers`) и раундами (`rounds`).
- **Round** — раунд сессии, содержит все `Offer` и `Decision` этого раунда.
- **Offer** — предложение от пропонента (`proposer`). Респондент (`responder`) назначается стратегией `ShuffleStrategy` после сбора всех офферов.
- **Decision** — решение респондента (accept/reject) по конкретному `Offer` (1:1).
- **SessionConfig** (`@Embeddable`) — параметры игры: тип, число раундов, число команд, число игроков, сумма раунда, таймаут хода.
- **ShuffleStrategy** — Strategy pattern для назначения `responder`: `FreeForAllStrategy` или `TeamBattleStrategy`.

## Роли (`Role`)

| Роль | Что делает |
|------|-----------|
| `ADMIN` | Создаёт сессии, управляет фазами (start / close / open / round.start / abort) |
| `PLAYER` | Присоединяется к сессии, отправляет офферы и решения |
| `OBSERVER` | Присоединяется как наблюдатель, читает события |
| `NPC` | Зарезервирована; регистрация запрещена (`AuthService.quickRegister`) |

## Запуск локально

Требования: JDK 21, Docker (для PostgreSQL).

```bash
# JWT-ключ (base64) — обязателен
export JWT_SIGNING_KEY=...

./gradlew bootRun
```

При запуске `spring-boot-docker-compose` автоматически стартует PostgreSQL из `compose.yaml:1-10` (`postgres/postgres`, порт 5432).

## Проверки

```bash
./gradlew test                # unit-тесты + JaCoCo html+xml report в build/reports/jacoco/test/
./gradlew check               # test + jacocoTestCoverageVerification (line coverage ≥80% для services/* и model.ShuffleStrategy — T-012)
./gradlew generateApiSnapshots # регенерация openapi.json + asyncapi.json в src/main/resources/doc/
```

## Точки входа для агента

| Задача | С чего начать |
|--------|---------------|
| Понять доменную модель | `docs/02-domain-model.md` |
| Изменить логику игры | `docs/03-state-machines.md` + `docs/04-services.md` |
| Добавить endpoint | `docs/05-rest-api.md` (или `06-`) + `docs/07-dto-and-mappers.md` |
| Изменить авторизацию | `docs/08-security.md` |
| Настроить окружение | `docs/10-configuration.md` |

## Внешние артефакты в репозитории

- `src/main/resources/doc/ultimatum-game.yaml` — OpenAPI 3 спека REST.
- `src/main/resources/doc/ws-ultimatum-game.yaml` — AsyncAPI спека WebSocket.
- `src/main/resources/doc/use-case-diagram.puml`, `class-diagram.puml`, `ultimatumgame_class_diagram.puml` — PlantUML.
- `src/main/resources/doc/old-er-diagram.md` — исходная ER-схема.
- `src/main/resources/index.sql` — DDL для триграмм-индекса поиска.
