# 12. Observability: логи, MDC, метрики, доменные события

Стандарт логирования и метрик, введённый в **T-017**. Дизайн — `docs/superpowers/specs/2026-07-13-observability-standard-design.md`.

## Формат логов

Профиль-зависимый (`logback-spring.xml`):

| Профиль | Формат | Куда |
|---------|--------|------|
| `dev` (по умолчанию) | plaintext | stdout |
| `prod` | JSON (одно событие = одна строка, `LogstashEncoder`) | stdout |

Переключение — через `SPRING_PROFILES_ACTIVE=prod` (или `application.properties: spring.profiles.active=prod`).

Ротация файлов — не делаем. Docker log driver / promtail (стадия 2) забирают stdout.

## MDC — обязательные поля

| Поле | Источник | Когда есть |
|------|----------|-----------|
| `traceId` | Micrometer Tracing (bridge-brave), автоматически | всегда |
| `spanId` | Micrometer Tracing, автоматически | всегда |
| `userId` | `JwtAuthenticationFilter` (HTTP) / `JwtStompChannelInterceptor` (WS) | после auth |
| `role` | тот же источник | после auth |
| `sessionId` | `util/MdcExt.withSessionMdc(sessionId) { ... }` — оборачивает игровые методы | вокруг игровой операции |
| `roundId` | `util/MdcExt.withRoundMdc(roundId) { ... }` | вокруг раунда |

**Правила:**
- `userId`/`role` — снимаются в `finally` в HTTP filter'е и в `afterSendCompletion` STOMP-интерцептора. Не пропускать через несколько запросов.
- `sessionId`/`roundId` — только через `withSessionMdc`/`withRoundMdc` (гарантируют `try/finally`).
- Своими руками `MDC.put`/`MDC.remove` не писать — использовать helper'ы.

## Level-конвенция (для технических логов)

Технические логи — свободный SLF4J через `logger()` extension.

| Level | Когда |
|-------|-------|
| ERROR | необработанное исключение, инвариант нарушен |
| WARN | восстановимая аномалия (retry, fallback, отклонённая валидация) |
| INFO | важные точки не-доменного потока (startup, миграции, важные side-effect'ы) |
| DEBUG | подробности для дебага (только dev-профиль) |
| TRACE | не используем |

Формулировка сообщения на русском (совпадает с текущим стилем в кодовой базе).

## Доменные события — жёсткий канал

Все события, важные для аналитики и Grafana, — **только через `DomainEventLogger`** (`util/DomainEventLogger.kt`). Свободные `log.info("session $id started")` для доменных событий — запрещены.

### API

```kotlin
@Component
class SomeService(private val domainEventLogger: DomainEventLogger) {
    fun doSomething(...) {
        domainEventLogger.emit(SessionCreated(sessionId, adminId, sessionType))
    }
}
```

Один вызов `emit()`:
1. Пишет структурированную запись в logger `domain-events` (в JSON-профиле — с полями события).
2. Инкрементит Micrometer counter `ultimatum.<event.type>` (теги — только с ограниченной кардинальностью: `role`, `sessionType`, `accepted`).

### Стартовый набор событий

| type | Точка вызова | Ключевые поля |
|------|--------------|--------------|
| `auth.register` | `AuthService.quickRegister` | userId, nickname, role |
| `auth.login` | `AuthService.quickLogin` | userId |
| `session.created` | `SessionService.createSession` | sessionId, adminId, sessionType |
| `session.started` | `AdminGameplayService.startSession` | sessionId, playerCount |
| `session.closed` | `AdminGameplayService.startNextRound` (когда раундов больше нет) | sessionId |
| `session.aborted` | `AdminGameplayService.abortSession` | sessionId |
| `player.joined` | `SessionService.joinSession`, `joinSessionAsObserver` | sessionId, userId, role |
| `player.left` | (пока не эмитится — feature не реализована) | — |
| `round.started` | `AdminGameplayService.startSession`, `startNextRound` | sessionId, roundId, roundNumber |
| `round.closed` | `AdminGameplayService.startNextRound`, `PlayerGameplayService.makeDecision` (когда все решения собраны) | sessionId, roundId, roundNumber |
| `offer.submitted` | `PlayerGameplayService.sendOffer` | sessionId, roundId, offerId, proposerId, amount |
| `offer.shuffled` | `CoreGameplayService.initWaitDecisionsPhase` | sessionId, roundId, offerId, proposerId, responderId |
| `decision.made` | `PlayerGameplayService.makeDecision` | sessionId, roundId, offerId, responderId, accepted, amount |

Добавление нового события: создать `data class Xxx : DomainEvent` в `util/DomainEvent.kt`, вызвать `domainEventLogger.emit(...)` в точке появления события.

## Метрики (Prometheus)

- Endpoint: `GET /actuator/prometheus`
- Registry: `micrometer-registry-prometheus` (см. `build.gradle.kts`)
- Экспозиция actuator сужена до `health,info,prometheus` (см. `application.properties`)

### Что доступно из коробки

- JVM: heap, threads, GC, classloader.
- HTTP: `http.server.requests` (timer c URI/method/status).
- Tomcat: threads.
- HikariCP: pool state, connection acquire time.

### Свои метрики

- `ultimatum.<event.type>` — counter, инкрементится в `DomainEventLogger.emit`.
- Теги: подмножество полей события с ограниченной кардинальностью (`role`, `sessionType`, `accepted`).
- UUID-поля (sessionId, roundId и др.) — **не в теги** (высокая кардинальность → взрывает Prometheus).

## Стадии деплоя

### Стадия 1 (сейчас — сделано в T-017)

- JSON-логи в stdout контейнера (в prod-профиле).
- `/actuator/prometheus` работает.
- Никакой внешней инфраструктуры. Данные для мониторинга уже текут.

### Стадия 2 (когда захочется дашбордов — отдельная задача)

Отдельный `docker-compose.observability.yml` рядом с приложением:

- **promtail** — читает stdout Docker-контейнера сервиса → отдаёт в Loki.
- **Loki** — хранилище логов.
- **Prometheus** — scrape `/actuator/prometheus` каждые N секунд.
- **Grafana** — datasource'ы: Loki + Prometheus. Дашборды через provisioning-файлы.

Приложение не трогаем — только запускаем observability-стек рядом.

### Стадия 3 (если понадобится distributed tracing — отдельная задача)

- Заменить `micrometer-tracing-bridge-brave` → `bridge-otel` + OTel-exporter.
- Развернуть Tempo/Jaeger.
- Приложение изменится только в `build.gradle.kts`, `logback-spring.xml` останется.

## См. также

- `docs/04-services.md` — `EventPublisherService`, `DomainEventLogger` в сервисах.
- `docs/10-configuration.md` — properties, профили.
- `docs/superpowers/specs/2026-07-13-observability-standard-design.md` — полный дизайн.
