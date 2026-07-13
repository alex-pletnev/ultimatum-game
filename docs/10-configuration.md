# 10. Конфигурация и окружение

## application.properties

Файл: `src/main/resources/application.properties`.

| Ключ | Значение | Комментарий |
|------|----------|-------------|
| `spring.application.name` | `ultimatum-game` | имя в metrics/logs |
| `server.servlet.context-path` | `/api/v1` | префикс всех endpoints |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | actuator сужен до health-check и Prometheus scrape (T-017) |
| `spring.profiles.active` | `dev` | по умолчанию dev; в prod — `SPRING_PROFILES_ACTIVE=prod` |
| `token.signing.key` | `${JWT_SIGNING_KEY}` | **обязательный env**, base64 |
| `spring.jpa.hibernate.ddl-auto` | `update` | автомиграция схемы |
| `spring.jpa.show-sql` | `true` | dev-логирование SQL (отключается в prod-профиле) |
| `spring.jpa.properties.hibernate.format_sql` | `true` | pretty-print SQL |
| `logging.level.root` | `INFO` | общий root-level |

**Профильные overrides** (T-017):
- `application-dev.properties` — `edu.itmo.ultimatumgame=DEBUG`, `spring.web=DEBUG`.
- `application-prod.properties` — `edu.itmo.ultimatumgame=INFO`, `spring.security=INFO`, `show-sql=false`.

**Формат логов** (T-017): `src/main/resources/logback-spring.xml` — plaintext для `!prod`, JSON (`LogstashEncoder`) для `prod`. Подробнее: `docs/12-observability.md`.

Настройки datasource не прописаны — их подставляет `spring-boot-docker-compose` (см. ниже) через service connection.

## Environment variables

| Var | Обязателен | Комментарий |
|-----|-----------|-------------|
| `JWT_SIGNING_KEY` | да | base64-строка для HS256, не короче 32 байт после декодирования |

Опционально можно переопределить через env любые ключи `application.properties` (`server.port`, `SPRING_DATASOURCE_*` и т. д.).

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
2. Hibernate накатывает схему (`ddl-auto=update`).
3. Приложение слушает `http://localhost:8080/api/v1`.
4. STOMP: `ws://localhost:8080/api/v1/ws`.
5. Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`.
6. Actuator: `http://localhost:8080/api/v1/actuator/**`.

### Инициализация индексов БД

`src/main/resources/index.sql` **не применяется автоматически** — нужно выполнить вручную (или добавить в миграции):

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_session_name_trgm
    ON session USING gin (display_name gin_trgm_ops);
```

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
