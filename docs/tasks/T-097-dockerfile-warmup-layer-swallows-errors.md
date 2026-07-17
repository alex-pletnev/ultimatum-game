---
id: T-097
title: Dockerfile warmup-слой `./gradlew dependencies ... || true` глотает ошибки
status: pending
priority: low
created: 2026-07-17
updated: 2026-07-17
related_code:
  - Dockerfile
related_docs:
  - docs/tasks/T-090-prod-deploy-readiness.md
tags: [tech-debt]
---

## Контекст

В `Dockerfile` (T-090 Phase 2) есть слой:

```dockerfile
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon dependencies -q || true
```

Цель — прогреть gradle-кэш зависимостей до копирования исходников. Но `|| true`
делает слой всегда «успешным»: если download зависимостей провалился (network,
maven-central down, wrong version), cache-слой всё равно commit'нется, а падение
всплывёт только на `bootJar` — с менее информативным сообщением.

Варианты:
1. Убрать `|| true` — падать сразу с понятной ошибкой.
2. Убрать весь warmup-слой — `bootJar` сам подтянет зависимости за 1 RUN.
   Кэш-выгода при повторных build'ах остаётся через `--mount=type=cache`.

## Acceptance criteria

- [ ] Либо `|| true` убран (падает fail-fast), либо warmup-слой выкинут (одна RUN на bootJar).
- [ ] `docker build -t ultimatum-game .` проходит с пустым cache и с warm cache — оба варианта.

## План

1. Проверить локально: убрать `|| true` → build пройдёт при первом запуске?
2. Если ок — коммит с fix'ом. Если нет — оставить warmup, но с явным `RUN ... || (echo "gradle deps failed" && exit 1)`.

## Лог

- 2026-07-17: заведено self-review'ом T-090 Phase 1+2 (commit 4065f8a). Категория B.
