CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_session_name_trgm
    ON session
        USING gin (display_name gin_trgm_ops);

CREATE UNIQUE INDEX IF NOT EXISTS ix_npc_profile_user_id
    ON npc_profile (user_id);

-- T-084: Hibernate ddl-auto=update не добавляет NOT NULL колонки без DEFAULT
-- в непустые таблицы. Дотягиваем SessionConfig.autoAdvanceRounds (T-076) явно
-- и идемпотентно до появления полноценной миграции (T-044).
ALTER TABLE session
    ADD COLUMN IF NOT EXISTS auto_advance_rounds BOOLEAN NOT NULL DEFAULT FALSE;