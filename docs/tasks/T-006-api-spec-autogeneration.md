---
id: T-006
title: Автогенерация REST/WS спек, удаление ручных YAML
status: done
priority: medium
created: 2026-07-12
updated: 2026-07-12
related_code:
  - build.gradle.kts
  - src/main/kotlin/edu/itmo/ultimatumgame/services/EventPublisherService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/AuthController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/SessionController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/UserController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/StatisticController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/CsrfController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/ws/OfferWsController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/ws/SessionAdminWsController.kt
  - src/main/resources/doc/ultimatum-game.yaml
  - src/main/resources/doc/ws-ultimatum-game.yaml
related_docs:
  - docs/05-rest-api.md
  - docs/06-websocket-api.md
  - docs/superpowers/specs/2026-07-12-api-spec-autogeneration-design.md
  - CLAUDE.md
tags: [api, docs, meta, tech-debt]
---

## Контекст

Ручные `src/main/resources/doc/ultimatum-game.yaml` и `ws-ultimatum-game.yaml` разошлись с реальным кодом. Ручная синхронизация непрактична. Переходим на автогенерацию: springdoc (уже подключён) для REST + springwolf-stomp для WS. Снапшоты коммитятся в git — дрейф контракта виден в diff. Полный дизайн: `docs/superpowers/specs/2026-07-12-api-spec-autogeneration-design.md`.

## Acceptance criteria

- [ ] Подключены `io.github.springwolf:springwolf-stomp` и `springwolf-ui` актуальной версии, совместимой со Spring Boot 3.4.
- [ ] `/springwolf/asyncapi-ui.html` открывается локально; все 4 SEND-эндпоинта и 5 топиков присутствуют.
- [ ] В `EventPublisherService` каждая публикация топика аннотирована `@AsyncPublisher` (5 методов).
- [ ] DTO с ограничениями (`CreateUserRequest.role` без `NPC`, `SessionConfigDto` инварианты) корректно отражены в openapi.json.
- [ ] `ApiErrorResponse` подключён как схема ошибок 4xx/5xx через `OpenApiCustomizer`.
- [ ] Gradle-таска `generateApiSnapshots` записывает `src/main/resources/doc/openapi.json` и `asyncapi.json` детерминированно (повторный запуск = тот же файл).
- [ ] Ручные `ultimatum-game.yaml` и `ws-ultimatum-game.yaml` удалены.
- [ ] `docs/05-rest-api.md` и `docs/06-websocket-api.md` — ссылки внизу обновлены на новые пути и URL springwolf UI.
- [ ] В `CLAUDE.md` добавлено правило: после Edit в `controllers/**`, `dto/**`, `EventPublisherService.kt` — прогонять `./gradlew generateApiSnapshots` перед commit.
- [ ] `./gradlew test` зелёный.

## План

1. Обновить `build.gradle.kts`: добавить springwolf-stomp + springwolf-ui; перенастроить `openApi { outputDir }` на `src/main/resources/doc/`.
2. Аннотировать паблишеры в `EventPublisherService.kt` — `@AsyncPublisher` для каждого `convertAndSend`.
3. Точечно `@Schema` в DTO там, где автоматика соврёт (enum-ограничения, cross-field инварианты, обёртки типа `Page<T>`).
4. `OpenApiCustomizer` для `ApiErrorResponse` — глобально на 4xx/5xx.
5. Написать Gradle-таску `generateAsyncApiSnapshot` (bootRun в фоне → curl `/springwolf/docs` → в файл). Обернуть с `generateOpenApiDocs` в `generateApiSnapshots`.
6. Прогнать `./gradlew generateApiSnapshots`, проверить содержимое.
7. Удалить `ultimatum-game.yaml`, `ws-ultimatum-game.yaml`.
8. Обновить нижние блоки в `docs/05-rest-api.md:163-166` и `docs/06-websocket-api.md:102-104`.
9. Дописать проактивный триггер в `CLAUDE.md`.
10. Ручная приёмка: Swagger UI + springwolf UI, сверка с narrative docs.

## Лог

- `2026-07-12`: задача заведена. Дизайн согласован (approach C — full auto-generation; snapshots коммитятся в git). WS-рефактор пойдёт отдельным таском после этого.
- `2026-07-12`: старт реализации. Начал с `build.gradle.kts` — springwolf deps + фикс `openApi.apiDocsUrl` (был без контекст-пути `/api/v1`, из-за чего `generateOpenApiDocs` бил в 404) + перенастройка `outputDir` на `src/main/resources/doc/`.
- `2026-07-12`: bump springdoc-openapi 2.2.0 → 2.7.0 (2.2.0 несовместим со Spring Boot 3.4, ловил `NoSuchMethodError: ControllerAdviceBean.<init>`).
- `2026-07-12`: попутные фиксы в SecurityConfig — `/v3/api/**` → `/v3/api-docs/**`, добавлены `swagger-ui/**`, `/webjars/**`, `/springwolf/**` в permitAll.
- `2026-07-12`: подход к генерации сменил с bootRun+curl на `@SpringBootTest` с H2 в тестовой среде (Docker недоступен в dev-окружении). `SpecSnapshotGeneratorTest` через MockMvc дампит оба JSON. Springdoc gradle plugin убран как не нужный.
- `2026-07-12`: аннотации springwolf на всех паблишерах (`EventPublisherService`, 5 методов) + WS-контроллерах (`SessionAdminWsController` 4 + `OfferWsController` 2 = 6 SEND). Итого AsyncAPI спека содержит 11 каналов.
- `2026-07-12`: DTO-уточнения — `CreateUserRequest.role` (allowableValues без NPC), `SessionConfigDto` (class-level Schema с инвариантами).
- `2026-07-12`: `OpenApiConfig` — Info-блок + `OpenApiCustomizer` привязывает `ApiErrorResponse` к 4xx/5xx.
- `2026-07-12`: старые `ultimatum-game.yaml`, `ws-ultimatum-game.yaml` удалены. `docs/05-rest-api.md`, `docs/06-websocket-api.md`, `CLAUDE.md` — обновлены (ссылки + правило регенерации).
- `2026-07-12`: **отступление от изначального дизайна:** custom gradle-таска `generateApiSnapshots` не сработала стабильно (Gradle 9 KTS странно кеширует). Оставил как единственный входной вызов `./gradlew test --tests "*.SpecSnapshotGeneratorTest"`. Регистрация — техдолг под отдельный меньший таск, если понадобится.
