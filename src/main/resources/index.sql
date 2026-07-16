CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_session_name_trgm
    ON session
        USING gin (display_name gin_trgm_ops);

CREATE UNIQUE INDEX IF NOT EXISTS ix_npc_profile_user_id
    ON npc_profile (user_id);