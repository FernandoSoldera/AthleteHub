-- AH-032: Day-of-week scheduling for workout templates.
--
-- Each row pins a template to a specific weekday so "today's plan" is a
-- single join: workout_templates ⨝ template_schedules WHERE day_of_week =
-- today. ISO numbering (Mon = 1 … Sun = 7) matches Java's
-- DayOfWeek.getValue() so the API layer doesn't need a translation table.
--
-- Design choices:
--   * Per-template, not per-user — templates already have owner_id, so
--     visibility is derived through the join. When Epic 7 adds coach
--     assignments, those land in a separate table (assignments) and the
--     "today" endpoint will UNION the two sources.
--   * UNIQUE(template_id, day_of_week) — a template can be scheduled at
--     most once per weekday. A user wanting two slots on the same day
--     creates two templates (Push AM, Push PM), which keeps the schema
--     simple and the UX honest.
--   * No CASCADE from workout_templates: when a template is deleted, its
--     schedule rows should die with it — the FK below uses ON DELETE
--     CASCADE for exactly that.

CREATE TABLE template_schedules (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id  BIGINT       NOT NULL REFERENCES workout_templates(id) ON DELETE CASCADE,
    day_of_week  SMALLINT     NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (template_id, day_of_week)
);

-- "What's planned for {user, today}?" hits (template owner_id, day_of_week)
-- via the join — index the schedule side so the lookup is one row out.
CREATE INDEX idx_template_schedules_day ON template_schedules (day_of_week, template_id);
