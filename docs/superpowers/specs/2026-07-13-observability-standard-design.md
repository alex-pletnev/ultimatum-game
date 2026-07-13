# Стандарт логирования и observability — дизайн

**Дата:** 2026-07-13
**Статус:** approved (brainstorming)
**Связанная задача:** T-017 (будет создана)

## Цель

Стандартизировать логирование в сервисе Ultimatum Game, чтобы:

1. Упростить разработку — унифицированный способ писать логи, готовый набор MDC-полей, отсутствие произвольной формы для доменных событий.
2. Дать минимальную observability после деплоя — JSON-логи в stdout + Prometheus-endpoint работают «из коробки», без внешней инфраструктуры.
3. Быть готовым к будущим Grafana-дашбордам (и логам, и метрикам) — без переписывания кода, только добавлением compose-стека.

## Ограничения (принятые пользователем в brainstorming'e)

- Минимум усилий на деплой; целевой сценарий — один VPS + Docker.
- Мониторинговой инфраструктуры пока нет — только приложение готово её принять.
- Grafana-дашборды — как будущее направление, не сейчас.
- Distributed tracing (Tempo/Jaeger) — не сейчас.
- Стандартизация — mixed: жёсткая для доменных событий (типизированный API), свободная для технических логов (SLF4J + level-конвенция в docs).

## Архитектура

### 1. Формат логов

- **Библиотека:** `net.logstash.logback:logstash-logback-encoder`.
- **Профили Spring:**
  - `dev` (по умолчанию) — plaintext-формат Logback, читаемо в консоли.
  - `prod` — JSON (одно событие = одна строка), в stdout.
- **Ротация / файлы** — не используем: stdout забирает Docker log driver, promtail (стадия 2) отдаёт в Loki.
- **Файлы конфигурации:**
  - `src/main/resources/logback-spring.xml` — appenders для обоих профилей.
  - `src/main/resources/application-dev.properties` — dev-only levels (DEBUG).
  - `src/main/resources/application-prod.properties` — prod levels (INFO, security=INFO).
  - Текущий `application.properties` — общие настройки.

### 2. MDC (Mapped Diagnostic Context)

**Обязательные поля на каждом логе:**

| Поле       | Источник                                              | Когда есть            |
|------------|-------------------------------------------------------|-----------------------|
| `traceId`  | Micrometer Tracing (bridge-brave), автоматически      | всегда                |
| `spanId`   | Micrometer Tracing, автоматически                     | всегда                |
| `userId`   | JWT после аутентификации                              | после auth (HTTP/WS)  |
| `role`     | JWT                                                   | после auth            |
| `sessionId`| вручную через `MDC.putCloseable` в сервисах           | вокруг игровых операций |
| `roundId`  | вручную                                               | вокруг раунда         |

**Точки заполнения:**

1. **`traceId`/`spanId`** — Micrometer Tracing делает это сам, без нашего кода.
2. **`userId`/`role`** — правки в `JwtAuthenticationFilter` (HTTP) и `JwtStompChannelInterceptor` (WS) в `try/finally` с `MDC.remove` на выходе.
3. **`sessionId`/`roundId`** — `MDC.putCloseable(...)` в сервисах (`CoreGameplayService`, `SessionService`, `AdminGameplayService`) вокруг игровых операций.

### 3. Доменные события — жёсткий канал

**Идея:** всё, что имеет смысл на будущих Grafana-дашбордах, идёт через единый `DomainEventLogger`. Никаких свободных `log.info("session $id started")`.

**Новые файлы:**

- `util/DomainEvent.kt` — sealed интерфейс + data-классы событий.
- `util/DomainEventLogger.kt` — сервис-фасад: пишет структурированный лог + инкрементит Micrometer counter.

**Sealed-иерархия событий (стартовый набор — 13):**

| Область     | События                                                        |
|-------------|----------------------------------------------------------------|
| Auth        | `auth.register`, `auth.login`                                  |
| Session     | `session.created`, `session.started`, `session.closed`, `session.aborted` |
| Membership  | `player.joined`, `player.left`                                 |
| Round       | `round.started`, `round.closed`                                |
| Gameplay    | `offer.submitted`, `offer.shuffled`, `decision.made`           |

**API логгера:**

```kotlin
@Component
class DomainEventLogger(private val meter: MeterRegistry) {
    private val log = LoggerFactory.getLogger("domain-events")

    fun emit(event: DomainEvent) {
        // 1) структурированный JSON-лог: event.type + fields
        // 2) meter.counter("ultimatum." + event.type, tags).increment()
    }
}
```

**Точка вызова:** `EventPublisherService.kt`. У него уже 5 методов, каждый фаннит событие в WS — рядом дописываем `domainEventLogger.emit(...)`. Auth-события — в `AuthService`.

**Дисциплина** (без detekt-правила на старте) — упоминание в `CLAUDE.md`: доменные события — только через `DomainEventLogger`. Если через 2-3 таска замечу свободный `log.info("session started")` — заведу follow-up на detekt-rule.

### 4. Технические логи — свободный канал

- Существующий `logger()` extension в `util/Logger.kt` — остаётся.
- Level-конвенция (в новом `docs/12-observability.md`):

| Level | Когда                                                               |
|-------|---------------------------------------------------------------------|
| ERROR | необработанное исключение, инвариант нарушен                        |
| WARN  | восстановимая аномалия (retry, fallback, отклонённая валидация)    |
| INFO  | важные точки не-доменного потока (startup, миграции)                |
| DEBUG | подробности для дебага, отключаемо в prod                           |
| TRACE | не используем                                                       |

**Правки в текущих `application.properties`:**
- Убрать `logging.level.org.springframework.security=DEBUG` (шумно + утекают токены).
- Убрать TRACE для STOMP.
- Общий уровень `edu.itmo.ultimatumgame` в базе — INFO, в `dev` — DEBUG.

### 5. Метрики

- **Зависимость:** `io.micrometer:micrometer-registry-prometheus` (сейчас её нет — `/actuator/prometheus` возвращает 404).
- **Endpoint:** `/actuator/prometheus`.
- **Экспозиция:** сузить `management.endpoints.web.exposure.include=health,info,prometheus` (сейчас `*` — торчит наружу лишнее).
- **Бесплатные метрики от Actuator:** JVM, HTTP-timers, Tomcat threads, HikariCP.
- **Свои метрики:** counter'ы для доменных событий (через `DomainEventLogger`, имена по Micrometer-конвенции: `ultimatum.session.created`, теги через `Tags.of(...)`).

### 6. Зависимости — что добавляем

```kotlin
// build.gradle.kts
implementation("net.logstash.logback:logstash-logback-encoder:8.0")
implementation("io.micrometer:micrometer-tracing-bridge-brave")
implementation("io.micrometer:micrometer-registry-prometheus")
```

Никаких OTel-экспортёров, Loki-appender'ов и Grafana-agent'ов — они появятся compose'ом на стадии 2, приложение о них знать не должно.

### 7. Стадии деплоя

**Стадия 1 (эта задача, T-017):**
- JSON-логи в stdout контейнера.
- `/actuator/prometheus` отдаёт метрики.
- Мониторинга нет, но данные для него уже течут.

**Стадия 2 (когда захочется дашбордов, отдельная задача, не сейчас):**
- Отдельный `docker-compose.observability.yml` → promtail + Loki + Prometheus + Grafana.
- Grafana-провижининг дашбордов через файлы.
- Приложение не трогаем.

**Стадия 3 (если понадобится distributed tracing):**
- `micrometer-tracing-bridge-otel` + OTel-exporter → Tempo.
- Не сейчас.

## План работ (высокий уровень)

1. Зависимости в `build.gradle.kts` + `logback-spring.xml` + профили properties.
2. Micrometer Tracing даст `traceId`/`spanId` в MDC автоматически — проверить.
3. `DomainEvent` + `DomainEventLogger`.
4. Правки `EventPublisherService` — вызовы `emit(...)` рядом с `messagingTemplate.convertAndSend`.
5. Правки `AuthService` — auth-события.
6. Правки `JwtAuthenticationFilter`, `JwtStompChannelInterceptor` — MDC.put/remove.
7. Правки сервисов — `MDC.putCloseable("sessionId", …)` вокруг игровых операций.
8. Сужение `management.endpoints.web.exposure.include`, чистка level'ов.
9. `docs/12-observability.md`, правки `docs/04-services.md`, `docs/10-configuration.md`.
10. Тесты: minimal — smoke-тест что `DomainEventLogger.emit` не падает, что MDC чистится в filter'ах.

Детальный порядок шагов — в implementation-плане (skill writing-plans).

## Границы (что НЕ входит)

- Установка Grafana/Loki/Prometheus/Tempo — отдельная задача, отдельный compose.
- Дашборды и алерты — не сейчас.
- OpenTelemetry-экспортёр — не сейчас.
- Ретеншн-политики, log-shipping — не сейчас.
- Правило detekt на запрет свободных доменных логов — follow-up при необходимости.

## Что было отвергнуто в brainstorming'e и почему

- **Свой `MdcFilter` для requestId** — есть штатный Micrometer Tracing.
- **Полная sealed-иерархия `DomainEvent` с обязательной публикацией в WS + лог** — over-engineering; сейчас достаточно рядом с `convertAndSend` вставить `emit`.
- **Рефакторинг `EventPublisherService` на Spring `ApplicationEvent`** — лишний слой, не даёт ценности.
- **detekt-правило запрета `LoggerFactory.getLogger` для доменных событий** — на старте держимся на дисциплине + CLAUDE.md; правило — follow-up если разъедется.
- **JSON-логи всегда, включая dev** — читать в консоли неудобно.
