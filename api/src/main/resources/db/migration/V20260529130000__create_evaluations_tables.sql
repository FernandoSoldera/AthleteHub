-- AH-040: Body / Evolution foundation — evaluations + their measurements
-- (13 circumferences + 8 skinfolds per the design, extensible). Columns
-- per docs/architecture/02-data-model.md §4.5 with the MVP simplifications:
--   * no client_uuid (online-first; no offline reconciliation yet)
--   * no body_metric_samples timescale hypertable (LATER — daily weight
--     from wearables ships when Epic 9's wearable sync arrives)
--   * no assigned_by_coach_id / eval_request_id FKs yet — those tables
--     (assignments, eval_requests) arrive in Epic 7. The source = 'coach'
--     enum value is already accepted so the data shape stays forward-
--     compatible; the FK columns get added by Epic 7's migration.
--
-- The graph series (weight / arm / waist / bench, ranges 4w/12w/6m/1y)
-- are derived at read time from evaluations + evaluation_measurements
-- (and personal_records for bench 1RM) — no "graph" table needed.

-- ── evaluations ───────────────────────────────────────────────────────────
-- One row per assessment. weight_kg is required because every assessment
-- captures it; body_fat_pct + bf_method are paired (a body-fat percentage
-- needs to record how it was computed) and both nullable for partial
-- evaluations (weight-only check-in). source distinguishes self-recorded
-- from coach-recorded — Epic 7 will populate 'coach' through assigned-
-- by-coach evaluations.
CREATE TABLE evaluations (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    evaluated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    weight_kg       NUMERIC(6,2) NOT NULL CHECK (weight_kg >= 0 AND weight_kg < 1000),
    body_fat_pct    NUMERIC(5,2) CHECK (body_fat_pct IS NULL OR (body_fat_pct >= 0 AND body_fat_pct <= 100)),
    bf_method       TEXT         CHECK (bf_method IS NULL OR bf_method IN
                                        ('jackson_pollock_7','durnin','navy','manual')),
    notes           TEXT,
    source          TEXT         NOT NULL DEFAULT 'self'
                                 CHECK (source IN ('self','coach')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- A body-fat percentage and its method should agree on presence: either
    -- both are filled (a real measurement) or neither is (weight-only check-in).
    CHECK ( (body_fat_pct IS NULL AND bf_method IS NULL)
         OR (body_fat_pct IS NOT NULL AND bf_method IS NOT NULL) )
);

-- The Evolution timeline + the metric-series graphs both scan by user,
-- newest first. Same shape as workout_sessions and cardio_activities so
-- cursor pagination is uniform across the app.
CREATE INDEX idx_evaluations_user_evaluated ON evaluations (user_id, evaluated_at DESC);

-- ── evaluation_measurements ───────────────────────────────────────────────
-- One row per (evaluation, point). point_id is a stable string key like
-- 'neck', 'chest', 'arm_r', 'tricep', 'suprail' — kept free-form TEXT so
-- new measurement points (a new skinfold site, a per-thigh circumference)
-- don't need a migration. kind discriminates how it was taken
-- (circumference vs skinfold); unit reinforces that ('cm' for
-- circumferences, 'mm' for skinfolds) so the UI can label without a
-- translation table.
CREATE TABLE evaluation_measurements (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evaluation_id   BIGINT       NOT NULL REFERENCES evaluations(id) ON DELETE CASCADE,
    point_id        TEXT         NOT NULL,
    kind            TEXT         NOT NULL CHECK (kind IN ('circumference','skinfold')),
    unit            TEXT         NOT NULL CHECK (unit IN ('cm','mm')),
    value           NUMERIC(6,2) NOT NULL CHECK (value >= 0),
    -- A given evaluation records each point at most once — re-measuring
    -- means editing the row, not appending another.
    UNIQUE (evaluation_id, point_id)
);

-- Reading an evaluation almost always pulls its measurements — index the
-- FK so the join is one step out without scanning.
CREATE INDEX idx_evaluation_measurements_eval ON evaluation_measurements (evaluation_id);
