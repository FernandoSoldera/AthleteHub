-- AH-050: Nutrition foundation — foods catalog, reusable diet plans
-- (meals → items), the day-by-day diary, and per-user favorites.
-- Columns per docs/architecture/02-data-model.md §4.6 with the MVP
-- simplifications:
--   * no client_uuid (online-first; no offline reconciliation yet)
--   * no assigned_by_coach_id FK on diet_plans yet — Epic 7's
--     assignments table hasn't landed. The diary_entries.source =
--     'coach' enum value is already accepted so the data shape stays
--     forward-compatible.
--   * tsvector / full-text search deferred — LOWER(name) index is
--     enough for AH-051's substring search, same call as exercises.

-- ── foods ─────────────────────────────────────────────────────────────────
-- Catalog. is_global = TRUE rows are the seed list shared by everyone
-- (created_by IS NULL); user customs belong to one user (created_by NOT
-- NULL). The XOR constraint mirrors the one on exercises (AH-030).
-- Macros are NUMERIC(7,2) so a 400 g chicken breast (~100 g protein) round-
-- trips cleanly; serving_size_g is the reference amount the macros are
-- given for (typically 100 g; could be 1 portion).
CREATE TABLE foods (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            TEXT         NOT NULL,
    brand           TEXT,
    is_global       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by      BIGINT       REFERENCES users(id) ON DELETE CASCADE,
    serving_size_g  NUMERIC(7,2) NOT NULL CHECK (serving_size_g > 0),
    kcal            NUMERIC(7,2) NOT NULL CHECK (kcal >= 0),
    protein_g       NUMERIC(7,2) NOT NULL CHECK (protein_g >= 0),
    carb_g          NUMERIC(7,2) NOT NULL CHECK (carb_g >= 0),
    fat_g           NUMERIC(7,2) NOT NULL CHECK (fat_g >= 0),
    fiber_g         NUMERIC(7,2) CHECK (fiber_g IS NULL OR fiber_g >= 0),
    sodium_mg       NUMERIC(7,2) CHECK (sodium_mg IS NULL OR sodium_mg >= 0),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK ( (is_global AND created_by IS NULL)
         OR (NOT is_global AND created_by IS NOT NULL) )
);

CREATE INDEX idx_foods_name_lower ON foods (LOWER(name));
CREATE INDEX idx_foods_created_by ON foods (created_by)
    WHERE created_by IS NOT NULL;

-- ── diet_plans ────────────────────────────────────────────────────────────
-- Reusable plans owned by a user. is_library = TRUE flags coach library
-- entries that can be assigned to multiple athletes (Epic 7).
CREATE TABLE diet_plans (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            TEXT         NOT NULL,
    description     TEXT,
    is_library      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_diet_plans_owner ON diet_plans (owner_id, id DESC);

-- ── diet_meals ────────────────────────────────────────────────────────────
-- A named meal inside a plan — "Breakfast", "Snack", "Post-workout".
-- time_hint is a free-form HH:MM TEXT; we don't parse it server-side, the
-- UI uses it to sort the day's meals on the diet screen.
CREATE TABLE diet_meals (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id         BIGINT       NOT NULL REFERENCES diet_plans(id) ON DELETE CASCADE,
    position        INTEGER      NOT NULL CHECK (position >= 0),
    name            TEXT         NOT NULL,
    time_hint       TEXT,
    UNIQUE (plan_id, position)
);

CREATE INDEX idx_diet_meals_plan ON diet_meals (plan_id, position);

-- ── meal_items ────────────────────────────────────────────────────────────
-- One food entry inside a meal — target amount + unit + position. The
-- RESTRICT on foods.id means a food can't be deleted out from under a
-- plan that references it; cascading from the user-delete path still
-- works because the meal_items go away first (diet_plans → diet_meals →
-- meal_items CASCADE) before foods.created_by CASCADE fires.
CREATE TABLE meal_items (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    meal_id         BIGINT       NOT NULL REFERENCES diet_meals(id) ON DELETE CASCADE,
    food_id         BIGINT       NOT NULL REFERENCES foods(id) ON DELETE RESTRICT,
    amount          NUMERIC(7,2) NOT NULL CHECK (amount >= 0),
    unit            TEXT         NOT NULL CHECK (unit IN ('g','ml','portion')),
    position        INTEGER      NOT NULL CHECK (position >= 0),
    UNIQUE (meal_id, position)
);

CREATE INDEX idx_meal_items_meal ON meal_items (meal_id, position);

-- ── diary_entries ─────────────────────────────────────────────────────────
-- What the user actually ate, when. meal_label is free-form so users can
-- bucket entries however they like ("Pre-workout", "Cheat meal") without
-- being forced into a fixed enum. source distinguishes self-logged from
-- plan-derived from favorite-quick-add from coach-assigned (the last one
-- is forward-compatible; Epic 7 will populate it).
CREATE TABLE diary_entries (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    food_id         BIGINT       NOT NULL REFERENCES foods(id) ON DELETE RESTRICT,
    eaten_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    amount          NUMERIC(7,2) NOT NULL CHECK (amount >= 0),
    unit            TEXT         NOT NULL CHECK (unit IN ('g','ml','portion')),
    meal_label      TEXT,
    source          TEXT         NOT NULL DEFAULT 'self'
                                 CHECK (source IN ('self','plan','favorite','coach')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- "Today's diary" / "week summary" both scan by user, newest first.
CREATE INDEX idx_diary_entries_user_eaten ON diary_entries (user_id, eaten_at DESC);

-- ── favorites ─────────────────────────────────────────────────────────────
-- Per-user food bookmarks for quick-add. CASCADE on food_id because a
-- favorite is just a pointer — losing the food makes the bookmark
-- meaningless.
CREATE TABLE favorites (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    food_id         BIGINT       NOT NULL REFERENCES foods(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, food_id)
);

CREATE INDEX idx_favorites_user ON favorites (user_id, id DESC);
