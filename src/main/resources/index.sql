CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_session_name_trgm
    ON session
        USING gin (display_name gin_trgm_ops);