# ER‑диаграмма базы данных **Ultimatum Game** (v 1.2)

> Согласовано с OpenAPI v1.2.0 и актуальной class‑diagram.

## Таблицы и связи

### `users`
| Поле       | Тип данных                                            | Описание                                   |
|------------|-------------------------------------------------------|--------------------------------------------|
| id         | UUID PRIMARY KEY                                      | Уникальный идентификатор пользователя      |
| nickname   | TEXT NOT NULL                                         | Ник игрока (анонимный)                     |
| role       | ENUM('ADMIN','PLAYER','OBSERVER','NPC') NOT NULL      | Роль пользователя                          |
| team_id    | UUID REFERENCES teams(id)                             | Принадлежность команде (NULL — без команды)|
| total_gain | INT DEFAULT 0                                         | Общий заработок                            |
| created_at | TIMESTAMP NOT NULL DEFAULT now()                      | Дата регистрации/присоединения             |

---

### `teams`
| Поле       | Тип данных                        | Описание                                  |
|------------|-----------------------------------|-------------------------------------------|
| id         | UUID PRIMARY KEY                 | Уникальный идентификатор                  |
| name       | TEXT NOT NULL                    | Название команды                          |
| session_id | UUID REFERENCES sessions(id)     | Ссылка на игровую сессию                  |
| score      | INT DEFAULT 0                    | Суммарный счёт команды                    |

---

### `sessions`
| Поле               | Тип данных                                           | Описание                               |
|--------------------|------------------------------------------------------|----------------------------------------|
| id                 | UUID PRIMARY KEY                                     | Уникальный идентификатор сессии        |
| state              | ENUM('CREATED','RUNNING','FINISHED','ABORTED') NOT NULL | Текущее состояние                      |
| num_rounds         | INT  NOT NULL                                        | Количество раундов                     |
| num_teams          | INT  NOT NULL DEFAULT 0                              | Количество команд                      |
| resource_per_round | INT  NOT NULL                                        | Ресурс (сумма) на раунд                |
| timeout_move_sec   | INT  NOT NULL                                        | Таймаут хода (сек.)                    |
| timeout_round_sec  | INT  NOT NULL                                        | Таймаут раунда (сек.)                  |
| created_at         | TIMESTAMP NOT NULL DEFAULT now()                     | Дата создания                          |
| admin_id           | UUID NOT NULL REFERENCES users(id)                   | Администратор‑создатель                |

---

### `rounds`
| Поле       | Тип данных                        | Описание                   |
|------------|-----------------------------------|----------------------------|
| id         | UUID PRIMARY KEY                 | Идентификатор раунда       |
| session_id | UUID REFERENCES sessions(id)     | Ссылка на сессию           |
| number     | INT NOT NULL                     | Номер раунда               |
| started_at | TIMESTAMP                        | Время начала               |
| ended_at   | TIMESTAMP                        | Время завершения           |

---

### `offers`
| Поле         | Тип данных                            | Описание                         |
|--------------|---------------------------------------|----------------------------------|
| id           | UUID PRIMARY KEY                     | Идентификатор предложения        |
| round_id     | UUID REFERENCES rounds(id)           | Ссылка на раунд                  |
| proposer_id  | UUID REFERENCES users(id)            | Пропонент                        |
| responder_id | UUID REFERENCES users(id)            | Респондент                       |
| amount       | INT  NOT NULL                        | Предлагаемая сумма               |
| created_at   | TIMESTAMP NOT NULL DEFAULT now()     | Дата и время создания            |

---

### `decisions`
| Поле       | Тип данных                        | Описание                         |
|------------|-----------------------------------|----------------------------------|
| id         | UUID PRIMARY KEY                 | Идентификатор решения            |
| offer_id   | UUID UNIQUE REFERENCES offers(id) | Ссылка на предложение (1:1)      |
| accepted   | BOOLEAN NOT NULL                 | Принято (`true`) / отклонено     |
| decided_at | TIMESTAMP NOT NULL DEFAULT now() | Время принятия решения           |

---

### `npc_strategies`
| Поле            | Тип данных            | Описание                                   |
|-----------------|-----------------------|--------------------------------------------|
| id              | UUID PRIMARY KEY     | Идентификатор стратегии                    |
| name            | TEXT NOT NULL        | Человекочитаемое имя                       |
| description     | TEXT                 | Подробное описание                         |
| threshold_low   | NUMERIC(5,2) NOT NULL| Нижний порог (%)                           |
| prob_low        | NUMERIC(5,2) NOT NULL| Вероятность при нижнем пороге (0–1)        |
| threshold_high  | NUMERIC(5,2) NOT NULL| Верхний порог (%)                          |
| prob_high       | NUMERIC(5,2) NOT NULL| Вероятность при верхнем пороге (0–1)       |

---

### `npc_links`
| Поле        | Тип данных                            | Описание                           |
|-------------|---------------------------------------|------------------------------------|
| player_id   | UUID PRIMARY KEY REFERENCES users(id) | NPC‑игрок                          |
| strategy_id | UUID REFERENCES npc_strategies(id)    | Привязанная стратегия              |

---

### `events`  *(для long‑polling)*
| Поле        | Тип данных                        | Описание                                  |
|-------------|-----------------------------------|-------------------------------------------|
| id          | BIGSERIAL PRIMARY KEY            | Порядковый идентификатор события          |
| session_id  | UUID REFERENCES sessions(id)     | Ссылка на сессию                          |
| type        | TEXT NOT NULL                    | Тип события (`OFFER_CREATED` и др.)       |
| happened_at | TIMESTAMP NOT NULL DEFAULT now() | Время события                             |
| payload     | JSONB                            | Детали события                            |

---

## Кардинальности

- **sessions 1 — N teams 1 — N users**
- **sessions 1 — N rounds 1 — N offers 1 — 1 decisions**
- **sessions 1 — N events**
- **npc_strategies 1 — N npc_links (players‑NPC)**

## Примечания

* `team_id` в `users` допускает `NULL`, что соответствует роли **OBSERVER** или игре без команд.
* Все значения времени по умолчанию проставляются сервером (`DEFAULT now()`).
* Агрегаты (общий счёт игроков/команд) вычисляются сервисом **StatisticsService** и кэшируются в `total_gain`, `score`.