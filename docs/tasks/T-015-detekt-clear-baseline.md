---
id: T-015
title: Выхлопать detekt baseline — починить все зафиксированные findings
status: in_progress
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - config/detekt/baseline.xml
  - src/
related_docs:
  - docs/tasks/T-014-setup-detekt-baseline.md
tags: [tech-debt, quality]
---

## Контекст

В T-014 подключили detekt с baseline — все текущие нарушения зафиксированы в `config/detekt/baseline.xml` и не ломают `check`. Задача этой — постепенно вычистить baseline: пройти по каждому findings, починить/сознательно suppress'нуть (`@Suppress` с комментарием), удалить `baseline.xml`. Blocked by T-014.

## Acceptance criteria

- [ ] Все findings из baseline либо починены в коде, либо явно suppress'нуты через `@Suppress("...")` с комментарием почему.
- [ ] `config/detekt/baseline.xml` удалён.
- [ ] `./gradlew detekt` (без baseline) зелёный.
- [ ] `./gradlew test` остался зелёный.
- [ ] Не меняется поведение — только стиль/структура кода.

## План

1. После T-014 открыть `config/detekt/baseline.xml`, сгруппировать findings по правилу (сортировкой).
2. Дробить работу на batch'и по правилу или пакету (naming, complexity, style, exceptions) — каждый batch отдельным commit'ом.
3. Для каждой группы: починить (переименовать, дробить, вынести константу), либо `@Suppress("RuleId") // причина` если fix не оправдан.
4. Периодически перегенерировать baseline, проверять регрессии.
5. В конце — удалить `baseline.xml`, убрать соответствующие ссылки из `build.gradle.kts` (или оставить пустой файл, если удобнее).

## Лог

- 2026-07-13: заведена одновременно с T-014 по явному запросу пользователя. Blocked until T-014 done.
- 2026-07-13: **Wave 1 — autofix через `./gradlew detektMain detektTest --auto-correct` (autoCorrect временно включён на время прогона).** Baseline main 579→311, test 59→59 (в test autofix не сработал, разбираться отдельно). Итого 638→370 findings. Тесты + `./gradlew check` зелёные. Затронуто 86 файлов — чисто стилевые правки (NewLineAtEndOfFile, NoConsecutiveBlankLines, ArgumentListWrapping, ParameterListWrapping, Indentation, spacing, semi, blank lines, imports order, MultiLineIfElse, EmptyClassBlock, AnnotationSpacing, StringTemplate, NoBlankLineBeforeRbrace). Никаких изменений в поведении.
- 2026-07-13: **Wave 2 — package rename `edu.itmo.ultimatum_game` → `edu.itmo.ultimatumgame`** (снимает PackageNaming + PackageName, 208 findings). Директории перенесены через `git mv src/{main,test}/kotlin/edu/itmo/ultimatum_game …/ultimatumgame`. Массовая правка контента через `sed 's/ultimatum_game/ultimatumgame/g'` по `.kt / .kts / .properties / .yml / .yaml / .json / .puml / .md` в `src/` + `docs/` + `build.gradle.kts` + `CLAUDE.md`. Файл `ultimatum_game_class_diagram.puml` переименован. Snapshots `openapi.json` + `asyncapi.json` перегенерированы через `generateApiSnapshots`. Baseline перегенерирован: main 311→135, test 59→27. Итого **638→162 findings**. `./gradlew check` — BUILD SUCCESSFUL.
- 2026-07-13: **Wave 3 — expand wildcard imports** (WildcardImport + NoWildcardImports, 42 findings). Скрипт (`perl -i -pe`) заменил `import java.util.*` (46 файлов; в основном на `UUID`, `Date`, где нужно — `HashMap`), `import jakarta.persistence.*` (6 моделей — по индивидуальному списку аннотаций/enum-значений), `import org.mapstruct.*` (10 mapper-ов; добавлены пропущенные `MappingConstants`, `NullValuePropertyMappingStrategy`), `import org.springframework.web.bind.annotation.*` (2 controllers), `import org.springframework.messaging.simp.SimpMessageType.*` (1), `import edu.itmo.ultimatumgame.model.*` + `.util.*` (по 1). Побочный эффект — новый ImportOrdering (11) + NoUnusedImports (+2), их подчистил повторный autofix. Итого **162→100 findings** (main 90, test 10). `./gradlew check` зелёный.
- 2026-07-13: **Wave 4 — drop `: Serializable` marker с DTO** (SerialVersionUIDInSerializableClass, 20 findings). Все 20 DTO (`dto/requests`, `dto/responses`) наследовали `java.io.Serializable` без осмысленной причины — response'ы едут через Jackson JSON, Java-сериализация не используется. Убрал интерфейс и `import java.io.Serializable`. Побочный: perl-regex съел trailing `\n` (появились NewLineAtEndOfFile ×20 + FinalNewline ×20), autofix починил. Итого **100→70 findings** (main 70, test 0 — test baseline пропал, detekt не генерирует пустой файл). `./gradlew check` зелёный.
- 2026-07-13: **Wave 5 — suppress UnnecessaryAbstractClass на MapStruct mapper'ах** (12 findings). Все 12 mapper'ов объявлены `abstract class` осознанно: MapStruct KAPT генерирует impl subclass — это стандартный паттерн, а не «случайно оставленный abstract». Добавил `@Suppress("UnnecessaryAbstractClass") // MapStruct generates impl subclass via kapt` перед каждым `abstract class …Mapper`. Итого **70→58 findings**. `./gradlew check` зелёный.
- 2026-07-13: **Wave 6 — `@file:Suppress("MaxLineLength", "MaximumLineLength")` на 11 файлах** (MaxLineLength + MaximumLineLength, 22 findings). Legitимные длинные строки: toString'ы моделей (`Decision`, `Offer`, `Round`, `Session`, `User`), long API `description = "..."` в аннотациях (`SessionConfigDto`, `EventPublisherService`), логгирование с интерполяцией (`AuthService`), длинные throw-messages (`SessionService`), inline-комментарий про round-transition (`AdminGameplayService`), длинный `if` в STOMP интерцепторе (`JwtStompChannelInterceptor`). В каждом случае разбивка ухудшила бы читаемость. Итого **58→36 findings**. `./gradlew check` зелёный.
