-- AH-052: Add `users.active_diet_plan_id` so a user can flag one diet
-- plan as their current. Nullable — a user without an active plan still
-- gets meaningful day totals (just no target / remaining numbers).
--
-- ON DELETE SET NULL because plans can be removed independently of users
-- (a coach revokes a library plan, the athlete edits a plan into
-- non-existence, etc.) — the active pointer should clear gracefully
-- rather than block the delete. The plan's content itself stays on
-- diet_plans → diet_meals → meal_items as before.

ALTER TABLE users
    ADD COLUMN active_diet_plan_id BIGINT
        REFERENCES diet_plans(id) ON DELETE SET NULL;

-- Lookup by active plan id is rare (a coach wanting to know "who's on
-- this plan?" is the only realistic case, and that lands with Epic 7's
-- assignments) — skip the index for now. The point-lookup by user_id
-- already uses the PK.
