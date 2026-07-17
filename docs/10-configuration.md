# 10. Конфигурация и окружение

## application.properties

Файл: `src/main/resources/application.properties`.

| Ключ | Значение | Комментарий |
|------|----------|-------------|
| `spring.application.name` | `ultimatum-game` | имя в metrics/logs |
| `server.servlet.context-path` | `/api/v1` | префикс всех endpoints |
| `server.port` | `${PORT:8080}` | (T-090) PaaS-хостеры прокидывают порт через `$PORT` |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | actuator сужен до health-check и Prometheus scrape (T-017); в prod overridден до `health,prometheus` (T-090) |
| `spring.profiles.active` | `dev` | по умолчанию dev; в prod — `SPRING_PROFILES_ACTIVE=prod` |
| `token.signing.key` | `${JWT_SIGNING_KEY}` | **обязательный env**, base64 |
| `spring.datasource.url` | `${DB_URL:jdbc:postgresql://localhost:5432/postgres}` | (T-090) externalized; dev-default совпадает с `compose.yaml` |
| `spring.datasource.username` | `${DB_USER:postgres}` | (T-090) externalized |
| `spring.datasource.password` | `${DB_PASSWORD:postgres}` | (T-090) externalized |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | (T-090) явно, а не через autodetect |
| `app.cors.origins` | `${APP_CORS_ORIGINS:http://localhost:[*]}` | (T-090) CSV-список origin'ов для CORS и WebSocket handshake; читается `@Value` в `SecurityConfiguration` и `WebSocketConfig` |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate только сверяет entity ↔ таблицы; схемой владеет Flyway (T-044) |
| `spring.jpa.show-sql` | `true` | dev-логирование SQL (отключается в prod-профиле) |
| `spring.jpa.properties.hibernate.format_sql` | `true` | pretty-print SQL |
| `spring.flyway.enabled` | `true` | миграции при старте (T-044); в тестах — `false` (H2) |
| `spring.flyway.baseline-on-migrate` | `true` | на непустой БД без `flyway_schema_history` — записать baseline и продолжить, не падать |
| `spring.flyway.baseline-version` | `0` | версия baseline; `V1__baseline.sql` применяется как первая настоящая миграция |
| `spring.flyway.locations` | `classpath:db/migration` | стандарт |
| `logging.level.root` | `INFO` | общий root-level |

**Профильные overrides** (T-017, T-090):
- `application-dev.properties` — `edu.itmo.ultimatumgame=DEBUG`, `spring.web=DEBUG`.
- `application-prod.properties` — `edu.itmo.ultimatumgame=INFO`, `spring.security=INFO`, `show-sql=false`, `spring.docker.compose.enabled=false` (T-090), `management.endpoints.web.exposure.include=health,prometheus` (T-090, убран `info` — не утекать build-метаданные).

**Формат логов** (T-017): `src/main/resources/logback-spring.xml` — plaintext для `!prod`, JSON (`LogstashEncoder`) для `prod`. Подробнее: `docs/12-observability.md`.

(T-090) Datasource externalized через `${DB_URL:...}`/`${DB_USER:...}`/`${DB_PASSWORD:...}`.
Dev-defaults совпадают с `compose.yaml`, поэтому `spring-boot-docker-compose` плагин
всё ещё поднимает контейнер при `bootRun`, а explicit env-vars идут в приоритет
на prod-профиле.

## Environment variables

| Var | Обязателен | Комментарий |
|-----|-----------|-------------|
| `JWT_SIGNING_KEY` | да | base64-строка для HS256, не короче 32 байт после декодирования |
| `DB_URL` | prod | (T-090) JDBC-URL Postgres. Dev-default — localhost из `compose.yaml` |
| `DB_USER` | prod | (T-090) юзер БД |
| `DB_PASSWORD` | prod | (T-090) пароль БД |
| `APP_CORS_ORIGINS` | prod | (T-090) CSV origin'ов для CORS и WS, e.g. `https://<gh-user>.github.io` |
| `PORT` | нет | (T-090) `${PORT:8080}`; PaaS-хостеры выставят сами |

Опционально можно переопределить через env любые ключи `application.properties` (`SPRING_DATASOURCE_*` и т. д.).

Полный prod-runbook — `docs/13-deploy.md` (T-090).

## compose.yaml

Файл: `compose.yaml`.

```yaml
services:
  postgres:
    image: 'postgres:latest'
    environment:
      - 'POSTGRES_DB=postgres'
      - 'POSTGRES_PASSWORD=postgres'
      - 'POSTGRES_USER=postgres'
    ports:
      - '5432:5432'
```

Плагин `spring-boot-docker-compose` (developmentOnly, `build.gradle.kts:47`) автоматически:
- поднимает контейнер при `bootRun`,
- прокидывает datasource-параметры в приложение,
- останавливает контейнер при завершении.

## build.gradle.kts

Файл: `build.gradle.kts`.

Плагины:

| Плагин | Версия | Строка |
|--------|--------|--------|
| `kotlin("jvm")` | 1.9.25 | :2 |
| `kotlin("plugin.spring")` | 1.9.25 | :3 |
| `org.springframework.boot` | 3.4.4 | :4 |
| `io.spring.dependency-management` | 1.1.7 | :5 |
| `kotlin("plugin.jpa")` | 1.9.25 | :6 |
| `kotlin("kapt")` | 2.1.20 | :7 |
| `org.springdoc.openapi-gradle-plugin` | 1.6.0 | :8 |

Ключевые версии:

| Библиотека | Переменная | Значение |
|-----------|-----------|----------|
| jjwt | `jjwtVersion` | 0.11.5 |
| MapStruct | `mapstructVersion` | 1.6.3 |
| commons-csv | inline | 1.11.0 |
| spring-security-config | inline | 6.1.0 |
| springdoc-openapi | inline | 2.2.0 |

Специфика конфигурации:

- `java.toolchain.languageVersion = JavaLanguageVersion.of(21)` — принудительно JDK 21 (`:14-18`).
- `kotlin.compilerOptions.freeCompilerArgs += "-Xjsr305=strict"` — строгие nullable-проверки JSR-305 (`:61-65`).
- `kapt.correctErrorTypes = true` — для MapStruct annotation processing (`:67`).
- `allOpen` для `@Entity`, `@MappedSuperclass`, `@Embeddable` — Kotlin classes становятся non-final для Hibernate proxy (`:81-85`).
- `openApi` task — генерирует `openapi.json` из работающего приложения (`:69-79`).

Основные dependencies:
```
spring-boot-starter-{actuator, data-jpa, security, validation, websocket, web}
spring-security-{messaging, config}
springdoc-openapi-starter-webmvc-ui
commons-csv
jjwt-{api, impl, jackson}
jackson-module-kotlin
kotlin-{reflect, noarg}
mapstruct + kapt(mapstruct-processor)
postgresql (runtime)
spring-boot-{devtools, docker-compose} (developmentOnly)
spring-boot-starter-test, kotlin-test-junit5, spring-security-test
```

## Запуск

### Локально

```bash
export JWT_SIGNING_KEY="$(openssl rand -base64 32)"
./gradlew bootRun
```

Что произойдёт:
1. Стартует PostgreSQL из `compose.yaml`.
2. Flyway накатывает миграции из `src/main/resources/db/migration/V*.sql` (T-044).
3. Hibernate `validate`'ит соответствие entity ↔ таблицы. Расхождение → приложение не стартует.
4. Приложение слушает `http://localhost:8080/api/v1`.
5. STOMP: `ws://localhost:8080/api/v1/ws`.
6. Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`.
7. Actuator: `http://localhost:8080/api/v1/actuator/**`.

### Схема БД и миграции (T-044)

**Полный контракт схемы — в миграциях Flyway.** `src/main/resources/db/migration/`:

- `V1__baseline.sql` — снапшот схемы после T-076 (сгенерирован `pg_dump --schema-only`
  из БД, поднятой через `@SpringBootTest` с `ddl-auto=update`). Включает `pg_trgm`
  extension, `idx_session_name_trgm` (GIN), `ix_npc_profile_user_id` (unique) —
  то что раньше жило в `IndexSqlInitializer` + `index.sql` (удалены в T-044).
- Дальнейшие изменения — новыми файлами `V2__<описание>.sql`, `V3__…` (только
  incremental DDL, не редактировать уже применённые версии).

**Как добавить миграцию:**

1. Изменить entity-класс (добавить поле, таблицу, etc).
2. Создать `V<next>__<snake_case_description>.sql` с DDL-изменением (ALTER TABLE / CREATE TABLE / ...).
3. Локально прогнать `./gradlew check` — Hibernate `validate` подтвердит совместимость.
4. `bootRun` — Flyway применит новую версию.

**Тесты используют H2** (`spring.flyway.enabled=false` в `src/test/resources/application.properties`),
Hibernate создаёт схему через `ddl-auto=create-drop`. Testcontainers/real-Postgres
для тестов — задача T-018.

### Тесты

```bash
./gradlew test
```

Есть 2 файла:
- `src/test/kotlin/edu/itmo/ultimatumgame/UltimatumGameApplicationTests.kt` — smoke `contextLoads`.
- `src/test/kotlin/edu/itmo/ultimatumgame/model/FreeForAllTest.kt` — тесты `FreeForAllStrategy`.

## Точка входа

`src/main/kotlin/edu/itmo/ultimatumgame/UltimatumGameApplication.kt`:

```kotlin
@SpringBootApplication
class UltimatumGameApplication

fun main(args: Array<String>) {
    runApplication<UltimatumGameApplication>(*args)
}
```

## См. также

- `docs/08-security.md` — конфигурация Security и STOMP.
- `docs/11-known-gaps.md` — риски открытого actuator, слишком широкого CORS и др.
