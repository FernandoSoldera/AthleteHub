-- AH-030: Training foundation — exercises catalog, reusable templates,
-- live sessions, sets, cardio, and personal records. Columns per
-- docs/architecture/02-data-model.md §4.4 with the MVP simplifications:
--   * no client_uuid (online-first; no offline reconciliation yet)
--   * no cardio_samples timescale hypertable (LATER)
--   * no PostGIS route geography on cardio_activities (LATER)
--   * no assignment_id FK on workout_sessions / cardio_activities yet —
--     the assignments table arrives in Epic 7. The `source = 'assigned'`
--     enum value is already accepted so the data shape stays forward-
--     compatible; the FK column gets added by Epic 7's migration.

-- ── exercises ─────────────────────────────────────────────────────────────
-- Catalog. `is_global = TRUE` rows are the seed list shared by everyone
-- (created_by is NULL); custom rows belong to one user (created_by NOT NULL).
-- The XOR constraint prevents an exercise from being both global and owned.
CREATE TABLE exercises (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            TEXT         NOT NULL,
    category        TEXT,
    primary_muscle  TEXT,
    equipment       TEXT,
    is_global       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by      BIGINT       REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK ( (is_global AND created_by IS NULL)
         OR (NOT is_global AND created_by IS NOT NULL) )
);

-- Search hits lower(name) for case-insensitive matching; the partial index
-- on created_by skips the wide global section when listing "my customs".
CREATE INDEX idx_exercises_name_lower ON exercises (LOWER(name));
CREATE INDEX idx_exercises_created_by ON exercises (created_by)
    WHERE created_by IS NOT NULL;

-- ── workout_templates ─────────────────────────────────────────────────────
-- Reusable plans owned by a user. `is_library = TRUE` flags coach library
-- entries that can be assigned to multiple athletes (Epic 7).
CREATE TABLE workout_templates (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            TEXT         NOT NULL,
    description     TEXT,
    is_library      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workout_templates_owner ON workout_templates (owner_id, id DESC);

-- ── workout_template_exercises ────────────────────────────────────────────
-- Ordered exercise list inside a template. `scheme` is a free-form label like
-- "4 × 6-8"; `target` is e.g. "80 kg". RESTRICT on exercises so the catalog
-- can't be pulled out from under a template.
CREATE TABLE workout_template_exercises (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id     BIGINT       NOT NULL REFERENCES workout_templates(id) ON DELETE CASCADE,
    exercise_id     BIGINT       NOT NULL REFERENCES exercises(id) ON DELETE RESTRICT,
    position        INTEGER      NOT NULL CHECK (position >= 0),
    scheme          TEXT,
    target          TEXT,
    UNIQUE (template_id, position)
);

CREATE INDEX idx_wte_template ON workout_template_exercises (template_id, position);

-- ── workout_sessions ──────────────────────────────────────────────────────
-- A performed session. Starts `in_progress`, becomes `completed` or
-- `abandoned`. The rollups (total_volume_kg, total_sets, pr_count) are
-- recomputed server-side on finish — the client's running estimate is just
-- UI feedback. duration_seconds is denormalized so weekly summary doesn't
-- have to derive it from started_at/ended_at on every read.
CREATE TABLE workout_sessions (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    template_id       BIGINT       REFERENCES workout_templates(id) ON DELETE SET NULL,
    title             TEXT         NOT NULL,
    status            TEXT         NOT NULL DEFAULT 'in_progress'
                      CHECK (status IN ('in_progress','completed','abandoned')),
    started_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ended_at          TIMESTAMPTZ,
    duration_seconds  INTEGER      CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    total_volume_kg   NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_sets        INTEGER      NOT NULL DEFAULT 0,
    pr_count          INTEGER      NOT NULL DEFAULT 0,
    source            TEXT         NOT NULL DEFAULT 'self'
                      CHECK (source IN ('self','assigned')),
    notes             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- "Recent sessions" + the Train hero card both scan by user, newest first.
CREATE INDEX idx_workout_sessions_user_started ON workout_sessions (user_id, started_at DESC);
-- Partial index to keep "what's the active session?" lookups cheap.
CREATE INDEX idx_workout_sessions_user_active ON workout_sessions (user_id)
    WHERE status = 'in_progress';

-- ── session_exercises ─────────────────────────────────────────────────────
-- Ordered exercise list inside a session. Seeded from the template (if any)
-- at session-start, then editable.
CREATE TABLE session_exercises (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id      BIGINT        NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    exercise_id     BIGINT        NOT NULL REFERENCES exercises(id) ON DELETE RESTRICT,
    position        INTEGER       NOT NULL CHECK (position >= 0),
    scheme          TEXT,
    target_weight   NUMERIC(7,2)  CHECK (target_weight IS NULL OR target_weight >= 0),
    UNIQUE (session_id, position)
);

CREATE INDEX idx_session_exercises_session ON session_exercises (session_id, position);

-- ── exercise_sets ─────────────────────────────────────────────────────────
-- One row per set. is_pr is set by the finish-session pass — kept here (not
-- only on personal_records) so the UI can highlight specific sets without
-- joining back through the PR table.
CREATE TABLE exercise_sets (
    id                   BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_exercise_id  BIGINT        NOT NULL REFERENCES session_exercises(id) ON DELETE CASCADE,
    set_number           INTEGER       NOT NULL CHECK (set_number >= 1),
    weight_kg            NUMERIC(7,2)  CHECK (weight_kg IS NULL OR weight_kg >= 0),
    reps                 INTEGER       CHECK (reps IS NULL OR reps >= 0),
    rpe                  NUMERIC(3,1)  CHECK (rpe IS NULL OR (rpe >= 0 AND rpe <= 10)),
    is_done              BOOLEAN       NOT NULL DEFAULT FALSE,
    is_pr                BOOLEAN       NOT NULL DEFAULT FALSE,
    completed_at         TIMESTAMPTZ,
    UNIQUE (session_exercise_id, set_number)
);

-- ── personal_records ──────────────────────────────────────────────────────
-- Current PR per (user, exercise, metric). The UNIQUE constraint enforces
-- "one row per PR" — history is reconstructable from the sessions table.
CREATE TABLE personal_records (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id   BIGINT        NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    metric        TEXT          NOT NULL CHECK (metric IN ('e1rm','max_weight','max_reps','volume')),
    value         NUMERIC(10,2) NOT NULL CHECK (value >= 0),
    achieved_at   TIMESTAMPTZ   NOT NULL,
    session_id    BIGINT        REFERENCES workout_sessions(id) ON DELETE SET NULL,
    UNIQUE (user_id, exercise_id, metric)
);

CREATE INDEX idx_personal_records_user ON personal_records (user_id, achieved_at DESC);

-- ── cardio_activities ─────────────────────────────────────────────────────
-- Run / walk / cycle. distance_m + duration_seconds are required; pace and
-- power are optional and may be supplied by wearable imports later.
CREATE TABLE cardio_activities (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                TEXT          NOT NULL CHECK (type IN ('run','walk','cycle')),
    distance_m          NUMERIC(10,2) NOT NULL CHECK (distance_m >= 0),
    duration_seconds    INTEGER       NOT NULL CHECK (duration_seconds >= 0),
    avg_pace_s_per_km   NUMERIC(7,2)  CHECK (avg_pace_s_per_km IS NULL OR avg_pace_s_per_km >= 0),
    avg_power_w         NUMERIC(7,2)  CHECK (avg_power_w IS NULL OR avg_power_w >= 0),
    avg_hr              INTEGER       CHECK (avg_hr IS NULL OR (avg_hr > 0 AND avg_hr < 300)),
    max_hr              INTEGER       CHECK (max_hr IS NULL OR (max_hr > 0 AND max_hr < 300)),
    elevation_gain_m    NUMERIC(7,2)  CHECK (elevation_gain_m IS NULL OR elevation_gain_m >= 0),
    kcal                INTEGER       CHECK (kcal IS NULL OR kcal >= 0),
    notes               TEXT,
    started_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    source              TEXT          NOT NULL DEFAULT 'self'
                        CHECK (source IN ('self','assigned','import')),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cardio_activities_user_started ON cardio_activities (user_id, started_at DESC);
