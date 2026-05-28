-- AH-014: Password reset codes — issued by /api/auth/password/forgot, consumed
-- by /api/auth/password/reset. Only the hash of the code is stored (codes are
-- short and would otherwise be brute-forceable from a DB leak).

CREATE TABLE password_reset_tokens (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);

CREATE INDEX idx_password_reset_tokens_user_active
    ON password_reset_tokens (user_id)
    WHERE used_at IS NULL;
