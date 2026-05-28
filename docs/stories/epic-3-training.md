# EPIC 3 — Training

Workouts (templates, live sessions, sets, PRs) and cardio (run/walk/cycle).
Layered backend; the live rest timer is client-side. See [02-data-model.md](../architecture/02-data-model.md)
for the schema basis (simplified for MVP: drop `client_uuid`, single schema).

---

## AH-030 — Schema: training tables
**Acceptance criteria**
- [ ] Migrations for: `exercises` (catalog), `workout_templates`, `workout_template_exercises`, `workout_sessions`, `session_exercises`, `exercise_sets`, `cardio_activities`, `personal_records`.
- [ ] FKs + indexes (`workout_sessions(user_id, started_at desc)`, `cardio_activities(user_id, started_at desc)`).
- [ ] Runs clean on Testcontainers.

**Technical notes** — Columns per `02-data-model.md §4.4` minus offline/`client_uuid`. `cardio_samples` time-series is **LATER** (not MVP).

## AH-031 — Exercise catalog
**Acceptance criteria**
- [ ] `GET /api/v1/exercises?q=` (search global + user-custom).
- [ ] `POST /api/v1/exercises` custom exercise.
- [ ] Seed migration with a starter exercise list (bench, squat, etc.).

## AH-032 — Today's plan + start session
**Acceptance criteria**
- [ ] `GET /api/v1/training/today` returns the planned session (from template or assignment) as the hero card data.
- [ ] `POST /api/v1/workout-sessions` starts a session (status `in_progress`), seeding `session_exercises` from the plan.

## AH-033 — Log sets + finish session
**Acceptance criteria**
- [ ] `PATCH /api/v1/workout-sessions/{id}` appends/updates/completes sets (weight, reps, done).
- [ ] `POST /api/v1/workout-sessions/{id}/finish` finalizes: server computes `total_volume_kg`, `total_sets`, detects PRs (e1RM/max weight) → upserts `personal_records`, sets status `completed`.
- [ ] IT: finishing a session computes the expected volume and records a PR.

**Technical notes** — Authoritative numbers computed server-side even though the client shows live estimates.

## AH-034 — Cardio logging
**Acceptance criteria**
- [ ] `POST /api/v1/cardio-activities` logs run/walk/cycle (distance, duration, avg pace/power, avg/max HR, elevation, kcal, notes).
- [ ] `GET /api/v1/cardio-activities?cursor=` lists recent.

## AH-035 — Recent sessions + weekly summary
**Acceptance criteria**
- [ ] `GET /api/v1/workout-sessions?cursor=` recent sessions (volume, duration, sets, PR count).
- [ ] `GET /api/v1/training/weekly-summary` (this-week cardio km + delta) for the Train chart.

## AH-036 — Client: Train / Workout / Cardio
**Acceptance criteria**
- [ ] `services/api/training_api_service.dart` + models.
- [ ] `screens/train_screen.dart` — today's plan hero, quick-log row, weekly cardio chart (`fl_chart`), recent sessions.
- [ ] `screens/workout_screen.dart` — live session: exercise list, set rows (weight/reps/check), **client-side rest timer**, progress bar + running volume; finish posts to backend.
- [ ] `screens/cardio_screen.dart` — run/walk/cycle form + pace chart + save.
- [ ] Online-first with loading/error/empty states; plain `setState`.

**Technical notes** — Match the design screens (`screens-train.jsx`). Charts via `fl_chart` widgets in `widgets/`.
