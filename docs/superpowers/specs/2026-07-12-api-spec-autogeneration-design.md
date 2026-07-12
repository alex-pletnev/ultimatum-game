# Дизайн: автогенерация OpenAPI и AsyncAPI спек

Дата: 2026-07-12
Статус: approved
Таск: T-006

## Проблема

Ручные YAML-спеки в `src/main/resources/doc/`:
- `ultimatum-game.yaml` (OpenAPI 3.1, 775 строк)
- `ws-ultimatum-game.yaml` (AsyncAPI 2.6, 212 строк)

расходятся с реальным кодом. Ручная синхронизация непрактична: слишком много контроллеров/DTO меняются между итерациями. Нужно снять с человека/агента обязанность держать спеки актуальными и переложить на инструмент.

## Решение

Полностью автогенерированные спеки:
- REST → `springdoc-openapi` (уже подключён в `build.gradle.kts:28`).
- WS/STOMP → `springwolf-stomp` (добавить).

Спеки существуют в двух формах:
1. **Runtime.** `/v3/api-docs` (REST) + `/springwolf/docs` (WS).
2. **Snapshot в git.** JSON-файлы дампятся Gradle-таской и коммитятся вместе с изменениями кода. Diff в PR/commit'е делает дрейф контракта видимым.

## Область — что делаем и что НЕ делаем

**Делаем:**
- Добавляем springwolf-stomp + springwolf-ui.
- Аннотируем публикацию топиков в `EventPublisherService` (springwolf их иначе не найдёт).
- Добавляем `@Schema` только там, где автоматика соврёт (enum ограничения, инварианты сложных DTO).
- Настраиваем Gradle-таску `generateApiSnapshots` (REST + WS в один заход).
- Удаляем ручные YAML.
- Обновляем `docs/05-rest-api.md`, `docs/06-websocket-api.md` (только ссылки внизу).
- Дописываем правило проактивной пересборки снапшотов в `CLAUDE.md`.

**НЕ делаем в этой задаче** (потенциально отдельные таски):
- Рефактор WS (убрать кастомный `PlaySessionStompChannelInterceptor` и др.) — пользователь планирует, но отдельно, после спек.
- Контрактный тест drift-guard (`assertEquals` снапшота с runtime-генерацией) — оставим как next-step, чтобы не раздувать скоуп.
- Закрытие спек под auth в prod-профиле.

## Компоненты

### Зависимости

Добавить в `build.gradle.kts`:

```kotlin
implementation("io.github.springwolf:springwolf-stomp:1.11.0")
implementation("io.github.springwolf:springwolf-ui:1.11.0")
```

Springwolf 1.11.x совместим со Spring Boot 3.4.x. Проверить актуальную минорную версию перед подключением.

### Аннотации в EventPublisherService

Публикации через `SimpMessagingTemplate.convertAndSend(...)` не видны springwolf'у автоматически. На каждый метод-публикатор — аннотация `@AsyncPublisher`:

```kotlin
@AsyncPublisher(
    operation = AsyncOperation(
        channelName = "/topic/session/{sessionId}/sessionStatus",
        payloadType = SessionWithTeamsAndMembersResponse::class
    )
)
fun publishSessionStatus(...) { ... }
```

Ожидаемое количество: ~5 методов (`sessionStatus`, `roundStatus`, `offerCreated`, `decisionMade`, `player/*/offer`).

### DTO аннотации

Пройти по `dto/requests` и `dto/responses`, добавить `@Schema` **только там, где нужно**:
- Enum-поля с ограничением на подмножество значений (пример: `CreateUserRequest.role` не допускает `NPC`).
- Сложные DTO с cross-field инвариантами (`SessionConfigDto`) — описание в `description`.
- Обёртки типа `Page<T>` — проверить, что springdoc-типизация не потерялась.

Bean Validation (`@Size`, `@Min`, `@NotBlank`) springdoc подхватывает без ручных аннотаций — не дублируем.

### Формат ошибок

Глобально через `OpenApiCustomizer`: добавить `ApiErrorResponse` как схему по умолчанию для `4xx`/`5xx` во всех endpoints. Не размазываем `@ApiResponse` по контроллерам.

### Gradle-таски

**REST:**
- Переконфигурировать существующий `openApi { outputDir }` в `build.gradle.kts:69-79` так, чтобы писал в `src/main/resources/doc/openapi.json`.
- `outputFileName.set("openapi.json")` уже стоит.

**WS:**
- Springwolf не даёт export-таску из коробки. Пишем `generateAsyncApiSnapshot`:
  - Вариант A: подтягиваем `AsyncApiService` из контекста, сериализуем, пишем в файл. Реализация — отдельный Gradle-плагин или тест-инструмент.
  - Вариант B (проще): таска зависит от `bootRun` в фоне → `curl http://localhost:8080/springwolf/docs > src/main/resources/doc/asyncapi.json` → останавливает bootRun.
  - Выбор: **B** — меньше кода, живёт вне production runtime. Реализуется через `JavaExec` + `Exec` таски.

**Объединяющая таска:**
```kotlin
tasks.register("generateApiSnapshots") {
    dependsOn("generateOpenApiDocs", "generateAsyncApiSnapshot")
}
```

### Расположение снапшотов

- `src/main/resources/doc/openapi.json`
- `src/main/resources/doc/asyncapi.json`

Удаляемые файлы:
- `src/main/resources/doc/ultimatum-game.yaml`
- `src/main/resources/doc/ws-ultimatum-game.yaml`

### Security

Endpoints springwolf добавить в `permitAll` в `SecurityConfig` (по аналогии с текущими swagger-ui path'ами). CSRF exclusion не нужен — это GET.

### CLAUDE.md — правило синхронизации

Дописать в раздел «Проактивные триггеры»:

| Условие | Skill/действие | Что делать |
|---------|-------|-----------|
| После Edit/Write в `controllers/**`, `dto/**`, `EventPublisherService.kt` | ручное действие | Прогнать `./gradlew generateApiSnapshots`, включить обновлённые `openapi.json` / `asyncapi.json` в тот же commit |

### Обновление narrative docs

`docs/05-rest-api.md:163-166` — заменить упоминание yaml на json.
`docs/06-websocket-api.md:102-104` — заменить упоминание yaml на json, дописать URL springwolf UI.

Содержательные таблицы endpoints оставляем — они не дублируют спеку, а служат навигацией для агентов с `file:line`.

## Порядок работы (для writing-plans)

1. Подключить `springwolf-stomp` + `springwolf-ui` в `build.gradle.kts`; поднять приложение, убедиться что `/springwolf/asyncapi-ui.html` отдаёт что-то.
2. Аннотировать `EventPublisherService` через `@AsyncPublisher`.
3. Пройти по DTO/контроллерам, добавить точечные `@Schema` и `OpenApiCustomizer` для формата ошибок.
4. Настроить Gradle: `openApi { outputDir }` → `src/main/resources/doc/`; написать `generateAsyncApiSnapshot`; объединить в `generateApiSnapshots`.
5. Прогнать таску, коммитнуть `openapi.json` и `asyncapi.json`.
6. Удалить старые YAML.
7. Обновить `docs/05-rest-api.md`, `docs/06-websocket-api.md`.
8. Дописать правило в `CLAUDE.md`.
9. Ручная сверка: открыть Swagger UI + springwolf UI, пройти по всем endpoints, что реальность == спека.

## Успех

- В `src/main/resources/doc/` лежат ровно два файла: `openapi.json`, `asyncapi.json`.
- Оба сгенерированы `./gradlew generateApiSnapshots`, дают тот же результат если запустить повторно (детерминизм).
- В обоих присутствуют все текущие endpoints (13 REST + 4 SEND + 5 топиков).
- В `CLAUDE.md` есть правило, что делать после изменений в controllers/dto/publisher.
- Ручной YAML удалён.
