---
id: T-017
title: Стандарт логирования и observability — JSON, MDC, доменные события, Prometheus
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - build.gradle.kts
  - src/main/resources/application.properties
  - src/main/kotlin/edu/itmo/ultimatumgame/services/EventPublisherService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/AuthService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/configs/JwtAuthenticationFilter.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/configs/JwtStompChannelInterceptor.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/CoreGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/AdminGameplayService.kt
related_docs:
  - docs/superpowers/specs/2026-07-13-observability-standard-design.md
  - docs/04-services.md
  - docs/10-configuration.md
  - docs/12-observability.md
tags: [observability, logging, metrics, tech-debt]
---

## Контекст

Логирование в сервисе сейчас несистемно: голый `logger()` extension, plaintext в консоль, `spring.security=DEBUG` и STOMP TRACE утекают токены/детали, `/actuator/prometheus` не работает (нет registry). Свободные `log.info("session $id ...")` для доменных событий разъезжаются, ловить закономерности в будущей Grafana нельзя.

Цель: стандартизировать. **Смешанный подход:**
- Технические логи — свободный SLF4J + level-конвенция в docs.
- Доменные события — жёсткий типизированный канал (`DomainEventLogger` + sealed `DomainEvent`).
- JSON-логи в prod, plaintext в dev.
- MDC: `traceId/spanId` (Micrometer Tracing автоматически) + `userId/role` (в filter'ах) + `sessionId/roundId` (в сервисах).
- Метрики: `micrometer-registry-prometheus`, counter'ы на доменные события.

Целевой сценарий деплоя — один VPS + Docker Compose, стек мониторинга (Loki+Prometheus+Grafana) — следующей задачей.

Полный дизайн: `docs/superpowers/specs/2026-07-13-observability-standard-design.md`.

## Acceptance criteria

- [ ] `build.gradle.kts`: добавлены `logstash-logback-encoder`, `micrometer-tracing-bridge-brave`, `micrometer-registry-prometheus`.
- [ ] `src/main/resources/logback-spring.xml` создан: dev — plaintext, prod — JSON в stdout.
- [ ] `application-dev.properties` / `application-prod.properties` — level'ы разведены по профилям.
- [ ] `util/DomainEvent.kt` — sealed интерфейс + 13 data-классов (auth×2, session×4, membership×2, round×2, gameplay×3).
- [ ] `util/DomainEventLogger.kt` — `@Component`, `emit(event)` пишет структурированный лог + инкрементит Micrometer counter.
- [ ] `EventPublisherService` — 5 вызовов `emit(...)` рядом с `messagingTemplate.convertAndSend`.
- [ ] `AuthService` — `emit(AuthRegister(...))` и `emit(AuthLogin(...))`.
- [ ] `JwtAuthenticationFilter` — `MDC.put("userId"/"role", …)` + `MDC.remove` в `finally`.
- [ ] `JwtStompChannelInterceptor` — то же для STOMP.
- [ ] Игровые сервисы — `MDC.putCloseable("sessionId", …)` вокруг игровых операций (минимум: `CoreGameplayService.submitOffer`, `submitDecision`; `AdminGameplayService.startRound`).
- [ ] `application.properties`: `management.endpoints.web.exposure.include=health,info,prometheus`; убран `security=DEBUG` и STOMP `TRACE`.
- [ ] `docs/12-observability.md` — новый: формат, MDC, список событий, level-конвенция.
- [ ] `docs/04-services.md` — обновлён (`EventPublisherService` теперь ещё и логирует).
- [ ] `docs/10-configuration.md` — обновлён (профили, новые properties).
- [ ] `./gradlew check` — зелёный.
- [ ] Задокументированы стадии деплоя (2 и 3) в `docs/12-observability.md`.

## План

1. Зависимости в `build.gradle.kts`.
2. `logback-spring.xml` + профили.
3. `DomainEvent.kt` + `DomainEventLogger.kt`.
4. Правки `EventPublisherService`, `AuthService`.
5. MDC в filter'ах и сервисах.
6. Сужение actuator exposure, чистка level'ов.
7. Документация (`docs/12`, правки `04`, `10`).
8. `./gradlew check` → commit → push.

## Лог

- 2026-07-13: заведена по brainstorming'у пользователя (стандартизация логов + подготовка к Grafana). Дизайн — в `docs/superpowers/specs/2026-07-13-observability-standard-design.md`.
- 2026-07-13: реализация — зависимости (logstash-encoder, micrometer-tracing-bridge-brave, micrometer-registry-prometheus), `logback-spring.xml` (dev plaintext / prod JSON), профили `application-{dev,prod}.properties`, `DomainEvent` sealed-иерархия (13 событий) + `DomainEventLogger`, MDC-хелперы `withSessionMdc`/`withRoundMdc`, MDC в `JwtAuthenticationFilter` и `JwtStompChannelInterceptor`, вызовы `emit(...)` в `SessionService`, `AuthService`, `AdminGameplayService`, `PlayerGameplayService`, `CoreGameplayService`, сужение actuator exposure, docs (`12-observability.md` + правки `04`, `10`, `README`). `./gradlew check` зелёный за 13с.
- 2026-07-13: побочно — вынес `dispatchOffers` в приватный метод в `CoreGameplayService` для fix'а detekt indentation (вылез из скоупа, оправдано локально). Подавил pre-existing detekt-findings в тестах через `@file:Suppress` — вынес в T-020, чинить нормально.
- 2026-07-13: диагностика — тесты `@SpringBootTest` требуют Postgres, `docker-compose up -d` в корне запускает `compose.yaml`. Плагин `spring-boot-docker-compose` работает только под `bootRun` (`developmentOnly`); под `test` его нет — см. T-018 про fail-fast.
