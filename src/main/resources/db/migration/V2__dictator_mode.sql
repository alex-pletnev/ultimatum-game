ALTER TABLE session DROP CONSTRAINT session_session_type_check;
ALTER TABLE session ADD CONSTRAINT session_session_type_check
    CHECK (session_type IN ('FREE_FOR_ALL', 'TEAM_BATTLE', 'DICTATOR'));
