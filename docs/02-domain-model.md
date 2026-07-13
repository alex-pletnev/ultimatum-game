# 02. Доменная модель и БД

Все сущности в пакете `edu.itmo.ultimatumgame.model`. ORM: Hibernate/JPA (Jakarta Persistence). Настройка: `spring.jpa.hibernate.ddl-auto=update` (`application.properties:7`).

## ER-схема (связи)

```
                                ┌───────────┐
                                │   USER    │
                                └─────┬─────┘
                       ┌──────────────┼──────────────┐
                (ADMIN)│    (MEMBERS) │  (OBSERVERS) │
                       │              │              │
                    ┌──▼──────────────▼──────────────▼──┐
                    │           SESSION                  │
                    │  id, displayName, state,           │
                    │  createdAt, openToConnect,         │
                    │  currentRound → Round (1:1),       │
                    │  config: SessionConfig (@Embedded) │
                    └──┬────────────────────┬────────────┘
             (1:N     │                     │  (1:N orphanRemoval)
              orphan) │                     │
              ┌───────▼──────┐        ┌─────▼──────────────┐
              │    TEAM       │        │      ROUND         │
              │ id, name      │        │ id, roundNumber,   │
              │ members M:N U │        │ roundPhase         │
              └───────────────┘        └────┬──────────┬────┘
                                            │          │
                                (1:N orphan)│          │(1:N orphan)
                                 ┌──────────▼───┐  ┌───▼──────────┐
                                 │    OFFER     │  │  DECISION    │
                                 │ proposer     │  │ responder    │
                                 │ responder?   │◄─┤ offer (1:1)  │
                                 │ offerValue   │  │ decision:Bool│
                                 └──────────────┘  └──────────────┘
```

Кардинальности:
- `Session` 1:N `Round`, 1:N `Team` (обе с `orphanRemoval=true`)
- `Round` 1:N `Offer`, 1:N `Decision` (обе с `orphanRemoval=true`)
- `Offer` 1:1 `Decision` (orphanRemoval на стороне `Decision`)
- `Session` M:N `User` (`members` — EAGER, `observers` — LAZY)
- `Team` M:N `User` (`members` — cascade MERGE)

## Сущности

### Session — `model/Session.kt:8-72`

Таблица: `session`.

| Поле | Тип | JPA | null? | Комментарий |
|------|-----|-----|-------|-------------|
| `id` | `UUID` | `@Id @GeneratedValue(UUID)` | PK | генерируется БД |
| `displayName` | `String` | `@Column(nullable=false)` | нет | используется в trgm-поиске |
| `state` | `SessionState` | `@Enumerated(STRING) @Column(nullable=false)` | нет | см. §Enum'ы |
| `createdAt` | `Date` | `@Column(nullable=false)` | нет | default = `Date()` |
| `admin` | `User` | `@ManyToOne(optional=false)` | нет | создатель |
| `openToConnect` | `Boolean` | — | — | default `true` |
| `currentRound` | `Round?` | `@OneToOne @JoinColumn("current_round_id")` | да | активный раунд |
| `rounds` | `MutableSet<Round>` | `@OneToMany(mappedBy="session", cascade=[PERSIST,MERGE,REMOVE], orphanRemoval=true)` | init | все раунды |
| `config` | `SessionConfig` | `@Embedded` | да | встроенный объект |
| `teams` | `MutableSet<Team>` | `@OneToMany(mappedBy="session", cascade=[PERSIST,MERGE,REMOVE], orphanRemoval=true)` | init | для TEAM_BATTLE |
| `members` | `MutableSet<User>` | `@ManyToMany(cascade=[MERGE], fetch=EAGER)` | init | активные игроки |
| `observers` | `MutableSet<User>` | `@ManyToMany(cascade=[MERGE], fetch=LAZY)` | init | наблюдатели |

Удаление `Session` каскадно удаляет все `Round` → `Offer` / `Decision` и все `Team` (но не `User`).

### SessionConfig — `model/SessionConfig.kt:8-25`

`@Embeddable` — колонки живут в таблице `session`.

| Поле | Тип | Комментарий |
|------|-----|-------------|
| `sessionType` | `SessionType` | `@Enumerated(STRING)`, обязательно |
| `numRounds` | `Int` | 1..10 (валидация DTO) |
| `numTeams` | `Int` | 0 для FREE_FOR_ALL, ≥2 для TEAM_BATTLE |
| `numPlayers` | `Int` | 2..120 |
| `roundSum` | `Int` | 10..100000, базовая сумма раунда |
| `timeoutMoveSec` | `Int` | 10..300, таймаут хода (в текущей версии не используется) |

### User — `model/User.kt:10-59`

Таблица: `users`. Реализует `UserDetails` (Spring Security), но `getPassword()` = `""` — авторизация только по JWT.

| Поле | Тип | JPA | Комментарий |
|------|-----|-----|-------------|
| `id` | `UUID` | `@Id @GeneratedValue(UUID)` | PK |
| `nickname` | `String` | `@Column(nullable=false)` | отображаемое имя |
| `role` | `Role` | `@Enumerated(STRING) @Column(nullable=false)` | ADMIN / PLAYER / OBSERVER / NPC |
| `createdAt` | `Date` | `@Column(nullable=false, updatable=false)` | immutable |

`UserDetails`:
- `getUsername()` → `id.toString()` (UUID)
- `getAuthorities()` → `[SimpleGrantedAuthority("ROLE_" + role.name)]`
- Все флаги (`isEnabled`, `isAccountNonExpired`, …) → `true`

### Team — `model/Team.kt:7-43`

Таблица: `team`.

| Поле | Тип | JPA |
|------|-----|-----|
| `id` | `UUID` | `@Id @GeneratedValue(UUID)` |
| `name` | `String` | `@Column(nullable=false)` |
| `members` | `MutableSet<User>` | `@ManyToMany(cascade=[MERGE])` |
| `session` | `Session` | `@ManyToOne(fetch=LAZY, optional=false), @JoinColumn("session_id", nullable=false)` |

### Round — `model/Round.kt:8-56`

Таблица: `round`.

| Поле | Тип | JPA |
|------|-----|-----|
| `id` | `UUID` | `@Id @GeneratedValue(UUID)` |
| `session` | `Session` | `@ManyToOne(fetch=LAZY, optional=false), @JoinColumn("session_id", nullable=false)` |
| `roundNumber` | `Int` | 1..N |
| `roundPhase` | `RoundPhase` | `@Enumerated(STRING) @Column(nullable=false)` |
| `offers` | `MutableList<Offer>` | `@OneToMany(mappedBy="round", cascade=[PERSIST,MERGE,REMOVE], orphanRemoval=true)` |
| `decisions` | `MutableList<Decision>` | `@OneToMany(mappedBy="round", cascade=[PERSIST,MERGE,REMOVE], orphanRemoval=true)` |

### Offer — `model/Offer.kt:8-56`

Таблица: `offer`.

| Поле | Тип | JPA | Комментарий |
|------|-----|-----|-------------|
| `id` | `UUID` | `@Id @GeneratedValue(UUID)` | |
| `session` | `Session` | `@ManyToOne(fetch=LAZY), @JoinColumn("session_id", nullable=false)` | shortcut для запросов |
| `round` | `Round` | `@ManyToOne(optional=false), @JoinColumn("round_id", nullable=false)` | |
| `proposer` | `User` | `@ManyToOne(optional=false), @JoinColumn("proposer_id", nullable=false)` | |
| `responder` | `User?` | `@ManyToOne, @JoinColumn("responder_id", nullable=true)` | `null` до `shuffleOffers()` |
| `offerValue` | `Int` | `@Column(nullable=false)` | 0..roundSum |
| `createdAt` | `Date` | `@Column(nullable=false, updatable=false)` | immutable |

**Инвариант:** `proposer.id != responder.id` (обеспечивается `ShuffleStrategy`).

### Decision — `model/Decision.kt:8-61`

Таблица: `decision`.

| Поле | Тип | JPA | Комментарий |
|------|-----|-----|-------------|
| `id` | `UUID` | `@Id @GeneratedValue(UUID)` | |
| `session` | `Session` | `@ManyToOne(fetch=LAZY), @JoinColumn("session_id", nullable=false)` | shortcut |
| `round` | `Round` | `@ManyToOne(optional=false), @JoinColumn("round_id", nullable=false)` | |
| `responder` | `User` | `@ManyToOne(optional=false), @JoinColumn("responder_id", nullable=false)` | |
| `offer` | `Offer` | `@OneToOne(cascade=[PERSIST,MERGE,REMOVE,REFRESH], optional=false, orphanRemoval=true), @JoinColumn(nullable=false)` | 1:1 |
| `decision` | `Boolean` | `@Column(nullable=false)` | true = accept, false = reject |
| `createdAt` | `Date` | `@Column(nullable=false, updatable=false)` | immutable |

**Инвариант:** `decision.responder.id == decision.offer.responder.id` (обеспечивается сервисом).

## Enum'ы

### SessionState — `model/SessionState.kt:5-20`

`CREATED`, `RUNNING`, `FINISHED`, `ABORTED`. Парсинг с `@JsonCreator`, case-insensitive.

Переходы см. `docs/03-state-machines.md`.

### RoundPhase — `model/RoundPhase.kt:5-22`

`CREATED`, `WAIT_OFFERS`, `ALL_OFFERS_RECEIVED`, `OFFERS_SENT`, `ALL_DECISIONS_RECEIVED`, `FINISHED`.

### SessionType — `model/SessionType.kt:5-18`

```kotlin
enum class SessionType(val shuffleStrategy: ShuffleStrategy) {
    FREE_FOR_ALL(FreeForAllStrategy()),
    TEAM_BATTLE(TeamBattleStrategy())
}
```

### Role — `model/Role.kt:5-21`

`ADMIN`, `PLAYER`, `OBSERVER`, `NPC`.

### ShuffleStrategy — `model/ShuffleStrategy.kt:6-68`

Интерфейс: `fun shuffleOffers(session: Session)`.

- **FreeForAllStrategy** (`:10-27`): назначает `responder` каждому `Offer` случайно из `session.members`, гарантируя `responder != proposer`. Каждый пользователь становится респондентом ровно один раз в раунде.
- **TeamBattleStrategy** (`:29-67`): назначает `responder` из **другой** команды. Если такого нет — `IllegalStateException`.

## Репозитории

Пакет `repositories/`.

| Репозиторий | Родитель | Кастомные методы |
|-------------|----------|-------------------|
| `SessionRepository` (`:12-32`) | `CrudRepository<Session, UUID>` | `findAll(pageable)`, `searchByNameTrgm(...)` — native pg_trgm search |
| `UserRepository` (`:9-10`) | `JpaRepository<User, UUID>` | нет |
| `TeamRepository` (`:4-6`) | `CrudRepository<Team, UUID>` | нет |
| `RoundRepository` (`:4-6`) | `CrudRepository<Round, UUID>` | нет |
| `OfferRepository` (`:11-24`) | `CrudRepository<Offer, UUID>` | `findAllBySessionIdWithRelations(sessionId)` — JPQL fetch join proposer/responder/round |
| `DecisionRepository` (`:9-25`) | `CrudRepository<Decision, UUID>` | `findAllBySessionIdWithRelations(sessionId)` — JPQL fetch join offer/responder/round |

### SessionRepository.searchByNameTrgm

Native SQL с `pg_trgm.similarity()` и `ILIKE` для ранжированного поиска по `display_name`. Требует индекс из `index.sql`.

### OfferRepository.findAllBySessionIdWithRelations

```jpql
select o from Offer o
    left join fetch o.proposer p
    left join fetch o.responder r
    left join fetch o.round rd
where o.session.id = :sessionId
```

Избавляет от N+1 при построении статистики / CSV-экспорте.

## Индексы БД

`src/main/resources/index.sql:1-5`:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_session_name_trgm
    ON session
        USING gin (display_name gin_trgm_ops);
```

Индексы на FK создаются Hibernate автоматически. Отдельно определён только GIN-индекс для триграмм-поиска.

## Инварианты и правила

Декларативные (JPA):

| Правило | Где | Эффект при нарушении |
|---------|-----|----------------------|
| NOT NULL на required полях | `@Column(nullable=false)`, `@ManyToOne(optional=false)` | DB constraint violation |
| Immutable `createdAt` | `@Column(updatable=false)` на `User.createdAt`, `Offer.createdAt`, `Decision.createdAt` | обновления игнорируются Hibernate |
| Каскад `Session` → `Round`/`Team` | `cascade=[PERSIST,MERGE,REMOVE], orphanRemoval=true` | удаление сессии = удаление всего дерева |
| Каскад `Round` → `Offer`/`Decision` | то же | |
| `Decision` orphan удаляет `Offer` | `@OneToOne(orphanRemoval=true)` на `Decision.offer` | удаление `Decision` удалит и `Offer` |

Бизнес-правила (проверяются в сервисах / стратегиях):

- `proposer.id != responder.id` — `ShuffleStrategy.kt:22`.
- В TEAM_BATTLE `responder` из другой команды — `ShuffleStrategy.kt:49-58`.
- Число офферов в раунде = `session.members.size` — `ShuffleStrategy.kt:15-16` (иначе исключение).
- `SessionConfig.numTeams == 0` ⇔ `FREE_FOR_ALL` — `SessionService.createTeams()`.

## Fetch-стратегии и производительность

| Связь | Fetch | Комментарий |
|-------|-------|-------------|
| `Session.members` | EAGER | компромисс: обычно нужны при загрузке сессии |
| `Session.observers` | LAZY | редкий доступ |
| `Session.currentRound` | (default) EAGER | 1:1 |
| `Round.session` | LAZY | избегаем цикла |
| `Offer.*`, `Decision.*` | LAZY | нагружаем через явный fetch join |

`DecisionRepository.findAllBySessionIdWithRelations` — fetch join по всем 3 связям (`DecisionRepository:14-24`), N+1 при `StatsService.getSessionStats` устранён (T-002).

## См. также

- `docs/03-state-machines.md` — переходы состояний.
- `docs/04-services.md` — где именно вызывается `shuffleOffers()` и как раунды сохраняются.
- `src/main/resources/doc/old-er-diagram.md` — исходная ER-схема автора.
