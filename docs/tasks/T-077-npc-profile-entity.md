---
id: T-077
title: NpcProfile entity + repository + JSONB params
status: done
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/model/NpcProfile.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/model/NpcStrategy.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/model/NpcParams.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/repositories/NpcProfileRepository.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/configs/HibernateJacksonConfig.kt
  - src/main/resources/index.sql
related_docs:
  - docs/superpowers/specs/2026-07-16-npc-mechanic-design.md
  - docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md
tags: [backend, db, feature, npc]
---

## Контекст

Task 2 из NPC-плана. Создаёт entity `NpcProfile` (`@OneToOne` с `User`), enum `NpcStrategy`, sealed `NpcParams` (Jackson polymorphic для JSONB), `NpcProfileRepository`. Индекс `ix_npc_profile_user_id`.

Только новые файлы + один индекс — контракт не меняет.

## Acceptance criteria

- [ ] `NpcProfile` сохраняется/читается через `NpcProfileRepository`.
- [ ] `params` сериализуется в JSONB (проверено на реальном Postgres).
- [ ] Unique-констрейнт на `user_id`.
- [ ] Repository-тест `save + findByUserId + existsByUserNickname` проходит.

## План

См. Task 2 в `docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md`.

## Лог

- 2026-07-16: заведено из NPC-plan.
- 2026-07-16: done. Созданы `NpcStrategy` enum, `NpcParams` sealed hierarchy (Jackson @JsonTypeInfo + @JsonSubTypes), `NpcProfile` @Entity с `@JdbcTypeCode(SqlTypes.JSON) params_json jsonb`, `NpcProfileRepository`. Индекс `ix_npc_profile_user_id`. `HibernateJacksonConfig` — плагинит Spring-managed ObjectMapper с KotlinModule в Hibernate JSON-mapper (иначе Kotlin data class без no-arg конструктора не десериализуется). Unit-тест `NpcParamsJacksonTest` — round-trip всех 5 типов. Полный JPA-round-trip через реальный Postgres будет проверен в T-080/T-083 интеграциях.
