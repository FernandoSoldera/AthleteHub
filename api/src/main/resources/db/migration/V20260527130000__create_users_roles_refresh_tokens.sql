-- AH-010: Identity foundation — users, roles, refresh tokens.
-- The schema choices below match docs/architecture/02-data-model.md §4.1
-- (simplified for MVP: no client_uuid, no separate per-context schema).

-- ── users ─────────────────────────────────────────────────────────────────
-- One row per account. Athletes and coaches share this table; the role is on
-- user_roles. password_hash is NULL for OAuth-only users (AH-015).
CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           TEXT        NOT NULL UNIQUE,
    password_hash   TEXT,                              -- NULL = OAuth-only
    full_name       TEXT        NOT NULL,
    handle          TEXT        NOT NULL UNIQUE,
    avatar_hue      INTEGER,                            -- 0-359, default-avatar tinting
    bio             TEXT,
    age             INTEGER     CHECK (age IS NULL OR (age >= 0 AND age < 130)),
    height_cm       NUMERIC(5,1) CHECK (height_cm IS NULL OR (height_cm > 0 AND height_cm < 300)),
    status          TEXT        NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active','suspended','deleted')),
    date_joined     DATE        NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

-- Email/handle are stored verbatim; the service normalizes to lowercase before
-- insert/lookup so uniqueness is case-insensitive without depending on citext.
CREATE INDEX idx_users_status ON users (status) WHERE deleted_at IS NULL;

-- ── user_roles ────────────────────────────────────────────────────────────
-- A user can hold multiple roles (ATHLETE and/or COACH) and switch between
-- them. ATHLETE is granted on signup; COACH on explicit upgrade (AH-016).
CREATE TABLE user_roles (
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        TEXT        NOT NULL CHECK (role IN ('ATHLETE','COACH')),
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role)
);

-- ── refresh_tokens ────────────────────────────────────────────────────────
-- Only the hash of the refresh token is stored — never the token itself.
-- Rotation + reuse-detection live in the service (AH-013).
CREATE TABLE refresh_tokens (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      TEXT        NOT NULL UNIQUE,
    device_info     TEXT,                                -- user-agent / platform, optional
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens (user_id)
    WHERE revoked_at IS NULL;
