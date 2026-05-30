-- AH-070: Coaching foundation — coach_athlete (the consent edge),
-- assignments (coach-prescribed tasks), eval_requests (coach asks for
-- a measurement), coach_profiles (public coach card). Columns per
-- docs/architecture/02-data-model.md §4.7 with the MVP simplifications:
--   * no client_uuid (online-first)
--   * no ratings flow yet — the columns are here so coach_profiles can
--     surface a rating from day one when AH-072+ wires it
--   * adherence_pct + flag are nullable until the recompute job (Epic 9)
--     populates them; the dashboard renders "—" / "new" until that lands
--
-- This migration ALSO wires the FK columns that earlier epics reserved
-- enum values for:
--   * workout_sessions.source = 'assigned' (AH-030) → finally points at
--     a real assignment via the new workout_sessions.assignment_id
--   * cardio_activities.source = 'assigned' (AH-034) → same column on
--     cardio_activities
--   * evaluations.source = 'coach' (AH-041) → evaluations.eval_request_id
-- All three FKs use ON DELETE SET NULL so a coach unassigning a task
-- doesn't blow away the athlete's already-completed session.

-- ── coach_athlete ────────────────────────────────────────────────────────
-- The relationship row. Also the dashboard row — flag + adherence_pct +
-- last_activity_at are the three coach-side tiles per athlete. Status
-- pending → active on consent (AH-071), active → ended on either side
-- canceling. UNIQUE(coach_id, athlete_id) means one relationship per
-- pair; a coach + athlete who ended and resumed get the same row
-- flipped, not a second insert.
CREATE TABLE coach_athlete (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coach_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    athlete_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status            TEXT         NOT NULL DEFAULT 'pending'
                                   CHECK (status IN ('pending','active','ended')),
    since             DATE,
    goal              TEXT,
    flag              TEXT         CHECK (flag IS NULL OR flag IN ('on_track','attention','risk')),
    adherence_pct     INTEGER      CHECK (adherence_pct IS NULL
                                          OR (adherence_pct >= 0 AND adherence_pct <= 100)),
    last_activity_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (coach_id, athlete_id),
    -- Can't coach yourself. (A user with both COACH and ATHLETE roles
    -- still can't coach their own ATHLETE side.)
    CHECK (coach_id <> athlete_id)
);

-- Coach dashboard filters by (coach, flag) — covered index.
CREATE INDEX idx_coach_athlete_coach_flag ON coach_athlete (coach_id, flag);
-- "Who is my coach?" / athlete-side lookups.
CREATE INDEX idx_coach_athlete_athlete ON coach_athlete (athlete_id);

-- ── assignments ──────────────────────────────────────────────────────────
-- A coach prescribes a workout / diet / eval to an athlete. ref_type +
-- ref_id are a soft link to the underlying asset (workout_template,
-- diet_plan, eval_request) — no FK because we don't want a template
-- delete to cascade-delete every assignment that ever referenced it.
-- Soft link goes stale; the coach can re-assign.
CREATE TABLE assignments (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coach_athlete_id   BIGINT       NOT NULL REFERENCES coach_athlete(id) ON DELETE CASCADE,
    type               TEXT         NOT NULL CHECK (type IN ('workout','diet','eval')),
    ref_type           TEXT,
    ref_id             BIGINT,
    scheduled_for      DATE,
    status             TEXT         NOT NULL DEFAULT 'scheduled'
                                    CHECK (status IN ('scheduled','today','pending','done','skipped')),
    notes              TEXT,
    notified_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- ref_type + ref_id move together — both null (a "free" assignment
    -- with just notes) or both set (linked to a template/plan/request).
    CHECK ( (ref_type IS NULL AND ref_id IS NULL)
         OR (ref_type IS NOT NULL AND ref_id IS NOT NULL) )
);

-- "Today's assignments for this athlete" — by relationship + date.
CREATE INDEX idx_assignments_relationship_date ON assignments (coach_athlete_id, scheduled_for);

-- ── eval_requests ────────────────────────────────────────────────────────
-- "Coach asks athlete for a measurement on date X" — distinct from
-- evaluations themselves (the athlete files the actual eval, which can
-- then reference this request). requested_points is the list of
-- circumferences / skinfolds the coach wants captured.
CREATE TABLE eval_requests (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coach_athlete_id   BIGINT       NOT NULL REFERENCES coach_athlete(id) ON DELETE CASCADE,
    scheduled_for      TIMESTAMPTZ  NOT NULL,
    requested_points   JSONB,
    reminder_at        TIMESTAMPTZ,
    notes              TEXT,
    status             TEXT         NOT NULL DEFAULT 'scheduled'
                                    CHECK (status IN ('scheduled','completed','missed')),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_eval_requests_relationship_scheduled
    ON eval_requests (coach_athlete_id, scheduled_for);

-- ── coach_profiles ───────────────────────────────────────────────────────
-- Coach-specific public card data. user_id is the PK so it's a strict 1:1
-- with users — the row exists when a user becomes a coach (AH-016) and
-- gets removed on user delete via CASCADE. Ratings columns ship now so
-- the coach card doesn't need a schema change when a rating feature
-- lands.
CREATE TABLE coach_profiles (
    user_id            BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    headline           TEXT,
    years_experience   INTEGER      CHECK (years_experience IS NULL OR years_experience >= 0),
    athlete_count      INTEGER      NOT NULL DEFAULT 0 CHECK (athlete_count >= 0),
    rating_avg         NUMERIC(3,2) CHECK (rating_avg IS NULL OR (rating_avg >= 0 AND rating_avg <= 5)),
    rating_count       INTEGER      NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── wire the FK targets earlier epics reserved enum values for ───────────
--
-- ON DELETE SET NULL on all three so unassigning a task (or removing an
-- eval_request) doesn't blow away the athlete's already-completed
-- session / activity / evaluation row — the link simply goes orphan.

ALTER TABLE workout_sessions
    ADD COLUMN assignment_id BIGINT
    REFERENCES assignments(id) ON DELETE SET NULL;
CREATE INDEX idx_workout_sessions_assignment ON workout_sessions (assignment_id)
    WHERE assignment_id IS NOT NULL;

ALTER TABLE cardio_activities
    ADD COLUMN assignment_id BIGINT
    REFERENCES assignments(id) ON DELETE SET NULL;
CREATE INDEX idx_cardio_activities_assignment ON cardio_activities (assignment_id)
    WHERE assignment_id IS NOT NULL;

ALTER TABLE evaluations
    ADD COLUMN eval_request_id BIGINT
    REFERENCES eval_requests(id) ON DELETE SET NULL;
CREATE INDEX idx_evaluations_eval_request ON evaluations (eval_request_id)
    WHERE eval_request_id IS NOT NULL;
