ALTER TABLE users ADD COLUMN recovery_code_hash varchar(64);
CREATE UNIQUE INDEX users_recovery_code_hash_idx ON users (recovery_code_hash);
