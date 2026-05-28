-- AH-015: OAuth identity links. A user can have multiple oauth_accounts rows
-- (one per provider). The mobile app obtains an ID token from Google / Apple
-- and posts it to /api/auth/oauth/{provider}; the backend verifies the token,
-- looks up (provider, provider_uid) here, and creates a row + the user on
-- first login.

CREATE TABLE oauth_accounts (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider      TEXT        NOT NULL CHECK (provider IN ('GOOGLE','APPLE')),
    provider_uid  TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_uid)
);

CREATE INDEX idx_oauth_accounts_user ON oauth_accounts (user_id);
