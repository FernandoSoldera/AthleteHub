# EPIC 5 — Nutrition

Diet plans, meals, the food database, daily diary, and macro tracking.

---

## AH-050 — Schema: nutrition tables
**Acceptance criteria**
- [ ] Migrations: `foods` (name, brand, barcode, default_qty/unit, kcal, carbs_g, protein_g, fat_g, is_global, created_by), `diet_plans` (user_id, name, source, assigned_by_coach_id null, target_kcal/protein/carbs/fat, active, start_date), `diet_meals` (plan_id, name, time_of_day, position), `meal_items` (meal_id, food_id, qty, qty_unit, macro snapshot), `diary_entries` (user_id, log_date, meal_name, food_id, qty, macro snapshot), `food_favorites` (user_id, food_id).
- [ ] tsvector + GIN index on `foods` for search; index `diary_entries(user_id, log_date)`.

## AH-051 — Food DB search + custom foods
**Acceptance criteria**
- [ ] `GET /api/v1/foods/search?q=` (full-text), `GET /api/v1/foods/favorites`, `POST /api/v1/foods` custom.
- [ ] Seed migration with a starter food list (from the design's `FOODS_DB`).
- [ ] Barcode lookup is **LATER** (stub the field).

## AH-052 — Active plan + day view
**Acceptance criteria**
- [ ] `GET /api/v1/diet/plan/active` returns the active plan + targets.
- [ ] `GET /api/v1/diet/day?date=` returns meals + items + computed totals (kcal/c/p/f) + remaining vs target (for the macro ring).

## AH-053 — Diary entries
**Acceptance criteria**
- [ ] `POST /api/v1/diary-entries` adds a food to a day/meal (snapshots macros).
- [ ] `DELETE /api/v1/diary-entries/{id}` removes one.
- [ ] Totals recompute correctly.

## AH-054 — Client: Diet screen + add food
**Acceptance criteria**
- [ ] `services/api/nutrition_api_service.dart` + models.
- [ ] `screens/diet_screen.dart` — macro ring hero (`fl_chart`), targets/remaining, day strip, meals with items.
- [ ] Add-food bottom sheet: search, recent/favourites tabs, tap-to-add.
- [ ] Loading/error/empty states; plain `setState`.

**Technical notes** — Match design `screens-diet-profile.jsx` (Diet portion). Macro ring as an `fl_chart` widget.
