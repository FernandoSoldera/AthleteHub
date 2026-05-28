# AthleteHub — MVP Backlog

The master, resumable map of work. Each story lives in its epic file under
`docs/stories/epic-*.md`. **Update the Status column here whenever a story moves**
— this table is how a new session knows where we left off.

- **Architecture:** layered backend + type-based Flutter, mirroring `lotuga`.
  See [architecture/CONVENTIONS.md](../architecture/CONVENTIONS.md) (authoritative).
- **Posture:** MVP first, online-first, REST only, mono-repo (`api/` + `client/`).

## How to use this across sessions

1. Open this file. Find the first story with status `TODO` in build order.
2. Open its epic file for full acceptance criteria + technical notes.
3. Implement it (copy the equivalent shape from `lotuga` when unsure).
4. Set its status to `DONE` here and check its boxes in the epic file. Commit.

**Status legend:** `TODO` · `WIP` (in progress) · `DONE` · `BLOCKED` · `LATER` (post-MVP).

## Suggested build order & MVP cut line

```
EPIC 0  Foundations            ──┐ build first, in order
EPIC 1  Identity & Auth          │  ← core athlete MVP
EPIC 3  Training                 │
EPIC 4  Body / Evolution         │
EPIC 5  Nutrition                │
EPIC 2  Social graph & profile   │
EPIC 6  Feed                   ──┘
EPIC 7  Coaching               ──┐ completes the coaching product
EPIC 8  Messaging                │
EPIC 9  Notifications & Media  ──┘
EPIC 10 Hardening & release    ──── ongoing / before launch
```

> **Minimum lovable MVP** = Epics 0,1,3,4,5 (an athlete can sign in, train, log
> evaluations and diet). Epics 2 & 6 make it social; 7–9 add the coach side;
> 10 is launch hardening. Re-prioritize freely.

## Story index

### EPIC 0 — Foundations · [epic-0-foundations.md](epic-0-foundations.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-001 | Initialize mono-repo (git, `api/`+`client/`+`docs/`, flatten docs, .gitignore) | DONE | — |
| AH-002 | Scaffold Spring Boot `api/` (pom mirror lotuga, profiles, .env, Postgres compose, Flyway baseline) | WIP | AH-001 |
| AH-003 | Scaffold Flutter `client/` (pubspec, folders, theme + i18n skeleton, app shell) | DONE | AH-001 |
| AH-004 | Backend cross-cutting (ApiResponse, global exception advice, WebConfig/CORS, IT harness) | WIP | AH-002 |

### EPIC 1 — Identity & Auth · [epic-1-identity-auth.md](epic-1-identity-auth.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-010 | Schema: users, roles, refresh_tokens (Flyway) | TODO | AH-004 |
| AH-011 | Register (email+password), password hashing, /me read | TODO | AH-010 |
| AH-012 | Login + JWT issuance, JwtUtil/Filter, SecurityConfig | TODO | AH-011 |
| AH-013 | Refresh-token rotation + logout | TODO | AH-012 |
| AH-014 | Password reset via email (GreenMail-tested) | TODO | AH-012 |
| AH-015 | Social login (Apple, Google) OAuth2 | TODO | AH-012 |
| AH-016 | Role switch (athlete/coach) + profile update | TODO | AH-011 |
| AH-017 | Client: auth screens, secure storage, http_interceptor, auth_api_service | TODO | AH-012, AH-003 |

### EPIC 2 — Social graph & profile · [epic-2-social-profile.md](epic-2-social-profile.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-020 | Schema: follows, user_counters | TODO | AH-010 |
| AH-021 | Follow/unfollow, followers/following | TODO | AH-020 |
| AH-022 | Find people (search) + suggestions | TODO | AH-021 |
| AH-023 | Public profile aggregate endpoint | TODO | AH-021 |
| AH-024 | Client: Find People + Profile screens, follow button | TODO | AH-023, AH-017 |

### EPIC 3 — Training · [epic-3-training.md](epic-3-training.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-030 | Schema: exercises, templates, sessions, session_exercises, sets, cardio, PRs | TODO | AH-010 |
| AH-031 | Exercise catalog endpoints + seed | TODO | AH-030 |
| AH-032 | Today's plan + start session | TODO | AH-031 |
| AH-033 | Log/complete sets + finish session (volume, PR detection) | TODO | AH-032 |
| AH-034 | Cardio logging (run/walk/cycle) | TODO | AH-030 |
| AH-035 | Recent sessions + weekly cardio summary | TODO | AH-033, AH-034 |
| AH-036 | Client: Train, live Workout (rest timer), Cardio screens + service | TODO | AH-033, AH-017 |

### EPIC 4 — Body / Evolution · [epic-4-body-evolution.md](epic-4-body-evolution.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-040 | Schema: evaluations, evaluation_measurements | TODO | AH-010 |
| AH-041 | Save evaluation + measurements + body-fat computation | TODO | AH-040 |
| AH-042 | Body overview + metric series (weight/arm/waist/bench, ranges) | TODO | AH-041 |
| AH-043 | Client: Evolution, New Evaluation (manikin), Graph detail + service | TODO | AH-042, AH-017 |

### EPIC 5 — Nutrition · [epic-5-nutrition.md](epic-5-nutrition.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-050 | Schema: foods, diet_plans, diet_meals, meal_items, diary_entries, favorites | TODO | AH-010 |
| AH-051 | Food DB search + seed + custom foods | TODO | AH-050 |
| AH-052 | Active diet plan + day endpoint (totals/remaining) | TODO | AH-050 |
| AH-053 | Diary entries (add food to a day) | TODO | AH-051, AH-052 |
| AH-054 | Client: Diet screen (macro ring, day strip), Add food sheet + service | TODO | AH-052, AH-017 |

### EPIC 6 — Feed · [epic-6-feed.md](epic-6-feed.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-060 | Schema: posts, post_likes, post_comments | TODO | AH-010 |
| AH-061 | Auto-create posts from workout/cardio/eval + manual posts | TODO | AH-060, AH-033 |
| AH-062 | Feed timeline (fan-out-on-read) + filters + hydration | TODO | AH-061, AH-021 |
| AH-063 | Like, comment, share | TODO | AH-062 |
| AH-064 | Client: Feed screen + card, like/comment + service | TODO | AH-063, AH-017 |

### EPIC 7 — Coaching · [epic-7-coaching.md](epic-7-coaching.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-070 | Schema: coach_athlete, assignments, eval_requests, coach_profiles | TODO | AH-010 |
| AH-071 | Coach↔athlete invite + consent linking | TODO | AH-070, AH-016 |
| AH-072 | Roster + adherence/flags + overview tiles | TODO | AH-071, AH-033 |
| AH-073 | Student detail aggregate | TODO | AH-072, AH-042 |
| AH-074 | Assign workout/diet/eval + schedule + library | TODO | AH-073, AH-030, AH-050 |
| AH-075 | Client: Students, Student detail, Assign, Schedule, Library, Coach profile | TODO | AH-074, AH-017 |

### EPIC 8 — Messaging · [epic-8-messaging.md](epic-8-messaging.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-080 | Schema: conversations, participants, messages | TODO | AH-070 |
| AH-081 | Conversations + messages endpoints (polling) + read state | TODO | AH-080 |
| AH-082 | Client: Inbox + chat screens + service | TODO | AH-081, AH-017 |

### EPIC 9 — Notifications & Media · [epic-9-notifications-media.md](epic-9-notifications-media.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-090 | Devices schema + register push token + Firebase Admin | TODO | AH-012 |
| AH-091 | In-app notifications + scheduled eval reminders (@Scheduled) | TODO | AH-090, AH-074 |
| AH-092 | Media upload (progress photos) + signed URLs | TODO | AH-004 |
| AH-093 | Client: push handling, notification inbox, image upload | TODO | AH-091, AH-092, AH-017 |

### EPIC 10 — Hardening & release · [epic-10-hardening-release.md](epic-10-hardening-release.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-100 | Rate limiting (bucket4j) + CORS + consistent errors | TODO | AH-012 |
| AH-101 | Observability (Actuator, Prometheus, structured logging) | TODO | AH-002 |
| AH-102 | CI (GitHub Actions: build+test api & client) + Dockerfile | TODO | AH-002, AH-003 |
| AH-103 | Trim/align legacy architecture docs to MVP (CONVENTIONS stays authoritative) | TODO | — |

---

**Progress:** 2 done, 2 in progress / 47.

### Session log
- **2026-05-27** — Epic 0 substantially complete.
  - **AH-001 DONE** — repo on `main`; docs flattened to `docs/`; root `.gitignore` + `README.md` + `CLAUDE.md`; `api/` + `client/` + `docs/` layout.
  - **AH-002 + AH-004 WIP** — backend `api/` fully scaffolded (pom mirroring lotuga, Maven wrapper, `com.example.athletehub` layered packages, `AthleteHubApplication`, `application.properties` + `it` profile, Flyway baseline `V20260527120000`, `.env.example`, `Dockerfile`, `docker-compose.yml`, `ApiResponse`/`MessageCode`, exceptions + `GlobalExceptionHandler`, `WebConfig`/`JacksonConfig`, `SecurityConfig` skeleton, `AbstractIntegrationTest` + `SmokeIT`). **To close:** build-verify needs **JDK 25** (machine `JAVA_HOME` is 24) + Docker → `cd api && ./mvnw verify` (runs `SmokeIT`) and `./mvnw spring-boot:run` → `/actuator/health` UP. Profiles follow lotuga (single `application.properties` + env vars + `it` test profile), not separate dev/prod YAML.
  - **AH-003 DONE** — Flutter `client/` scaffolded (`flutter create --org com.example --project-name athletehub --platforms=android,ios`); custom `pubspec.yaml` (http, dotenv, secure_storage, svg, intl, fl_chart, firebase_core/messaging, google_sign_in, sign_in_with_apple; dev: integration_test, mockito, build_runner, flutter_lints); type-based `lib/` folders (`config i18n models[+responses] screens services[+api] styles widgets`); `AppTheme.themeFor` (dark + light × 4 accent palettes from `tokens.css`); `AppLocalizations` + `en.json` + `pt.json`; `main.dart` shell with 5-tab `NavigationBar` (Feed/Train/Evolve/Diet/Me) via `PlaceholderScreen`. Verified: `flutter pub get` resolves, `flutter analyze` clean (no issues), `flutter test` passes the smoke widget test.
  - **Next:** verify the backend on JDK 25 → flip AH-002/004 to DONE; then EPIC 1 (Identity & Auth) starting with **AH-010** (schema).
