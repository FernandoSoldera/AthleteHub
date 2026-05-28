# EPIC 7 — Coaching

The coach side: roster, student detail, assigning workouts/diets/evals, schedule,
library. Access is **relationship-gated + consent-based** (a coach sees only their
linked athletes). Same Flutter app, coach mode.

---

## AH-070 — Schema: coaching tables
**Acceptance criteria**
- [ ] `coach_athlete` (id, coach_id, athlete_id, status CHECK(pending|active|ended), since, goal, flag CHECK(on-track|attention|risk), adherence_pct, last_activity_at), UNIQUE(coach_id, athlete_id), index(coach_id, flag).
- [ ] `assignments` (id, coach_athlete_id, type CHECK(workout|diet|eval), ref_type, ref_id, scheduled_for, status, notes, notified_at).
- [ ] `eval_requests` (id, coach_athlete_id, scheduled_for, requested_points jsonb, reminder_at, notes, status).
- [ ] `coach_profiles` (user_id PK, headline, years_experience, athlete_count, rating_avg, rating_count).

## AH-071 — Invite + consent linking
**Acceptance criteria**
- [ ] `POST /api/v1/coach/invitations` (coach invites an athlete by handle/email) → `pending`.
- [ ] `POST /api/v1/coach/invitations/{id}/accept` (athlete consents) → `active`; athlete can end the link (revokes coach access).
- [ ] A policy helper `canCoachAccess(coachId, athleteId)` enforced on all coach endpoints.

## AH-072 — Roster + overview
**Acceptance criteria**
- [ ] `GET /api/v1/coach/athletes?flag=` roster with adherence + flag + last activity.
- [ ] `GET /api/v1/coach/overview` summary tiles (count, need check-in, this week).
- [ ] `adherence_pct` + `flag` recomputed by a nightly `@Scheduled` job and on workout/eval completion (direct call).

## AH-073 — Student detail aggregate
**Acceptance criteria**
- [ ] `GET /api/v1/coach/athletes/{id}/overview` — profile, latest evals + bodyweight series, week plan, adherence (one payload for the screen). Gated by policy.

## AH-074 — Assign + schedule + library
**Acceptance criteria**
- [ ] `POST /api/v1/coach/athletes/{id}/assign/workout|diet`, `POST .../eval-requests` (creates assignment/eval_request + schedules a reminder).
- [ ] `GET /api/v1/coach/schedule?week=` (assignments per day).
- [ ] `GET /api/v1/coach/library?kind=workout|exercise|diet` (templates with `is_library`).

## AH-075 — Client: coach screens
**Acceptance criteria**
- [ ] `services/api/coaching_api_service.dart` + models.
- [ ] Screens: students dashboard (summary + filter chips + cards), student detail, assign workout/diet/eval, schedule, library, coach profile.
- [ ] Role switch toggles the tab set (Students/Schedule/Library/Inbox/Coach).
- [ ] Plain `setState`; loading/error/empty.

**Technical notes** — Match design `screens-teacher.jsx` + the teacher screens in `app.jsx`.
