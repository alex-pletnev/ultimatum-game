ALTER TABLE session ADD COLUMN anonymous boolean NOT NULL DEFAULT false;
ALTER TABLE session ADD COLUMN anonymity_key uuid NOT NULL DEFAULT gen_random_uuid();
CREATE TABLE session_autopilot (
    session_id uuid NOT NULL REFERENCES session(id),
    user_id uuid NOT NULL REFERENCES users(id),
    PRIMARY KEY (session_id, user_id)
);
ALTER TABLE offer ADD COLUMN automatic boolean NOT NULL DEFAULT false;
ALTER TABLE decision ADD COLUMN automatic boolean NOT NULL DEFAULT false;
