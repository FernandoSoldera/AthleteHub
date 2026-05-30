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
- [x] `services/api/coach_api_service.dart` + 6 models
      (`CoachInvite`, `RosterEntry`, `MyCoach`, `StudentDetail`,
      `Assignment`, `CoachProfile`).
- [x] Screens: students roster (filterable), student detail
      (rollups + assignments + Assign sheet), assign workout/diet/eval
      sheet, pending invites inbox (athlete), my assignments
      (athlete), coach profile setup, invite-athlete sheet.
- [x] Coaching hub embedded in the existing profile screen
      (Option B — single tab tree). Both athlete-side actions
      (invites, assignments, current coach card) and coach-side
      actions (athletes roster, coach profile) coexist;
      role-switching still works server-side, but the UI is
      shared.
- [x] Plain `setState`; loading / error / empty states on every
      screen.

**Technical notes** — Backend follow-up: `GET / PUT /api/me/coach-profile`
with a lazy-default + partial-update pattern (no row written
until the first PUT). Schedule + Library deferred to a later
sub-story; for MVP, the Assign sheet lets coaches schedule a
date + freeform notes — template / plan pickers will arrive
when authoring screens for `workout_templates` /
`diet_plans` land.
