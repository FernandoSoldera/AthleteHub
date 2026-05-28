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
| AH-002 | Scaffold Spring Boot `api/` (pom mirror lotuga, profiles, .env, Postgres compose, Flyway baseline) | DONE | AH-001 |
| AH-003 | Scaffold Flutter `client/` (pubspec, folders, theme + i18n skeleton, app shell) | DONE | AH-001 |
| AH-004 | Backend cross-cutting (ApiResponse, global exception advice, WebConfig/CORS, IT harness) | DONE | AH-002 |

### EPIC 1 — Identity & Auth · [epic-1-identity-auth.md](epic-1-identity-auth.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-010 | Schema: users, roles, refresh_tokens (Flyway) | DONE | AH-004 |
| AH-011 | Register (email+password), password hashing, /me read | DONE | AH-010 |
| AH-012 | Login + JWT issuance, JwtUtil/Filter, SecurityConfig | DONE | AH-011 |
| AH-013 | Refresh-token rotation + logout | DONE | AH-012 |
| AH-014 | Password reset via email (GreenMail-tested) | DONE | AH-012 |
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

**Progress:** 9 done / 47.

### Session log
- **2026-05-27** — Epic 0 substantially complete.
  - **AH-001 DONE** — repo on `main`; docs flattened to `docs/`; root `.gitignore` + `README.md` + `CLAUDE.md`; `api/` + `client/` + `docs/` layout.
  - **AH-002 + AH-004 WIP** — backend `api/` fully scaffolded (pom mirroring lotuga, Maven wrapper, `com.example.athletehub` layered packages, `AthleteHubApplication`, `application.properties` + `it` profile, Flyway baseline `V20260527120000`, `.env.example`, `Dockerfile`, `docker-compose.yml`, `ApiResponse`/`MessageCode`, exceptions + `GlobalExceptionHandler`, `WebConfig`/`JacksonConfig`, `SecurityConfig` skeleton, `AbstractIntegrationTest` + `SmokeIT`). **To close:** build-verify needs **JDK 25** (machine `JAVA_HOME` is 24) + Docker → `cd api && ./mvnw verify` (runs `SmokeIT`) and `./mvnw spring-boot:run` → `/actuator/health` UP. Profiles follow lotuga (single `application.properties` + env vars + `it` test profile), not separate dev/prod YAML.
  - **AH-003 DONE** — Flutter `client/` scaffolded (`flutter create --org com.example --project-name athletehub --platforms=android,ios`); custom `pubspec.yaml` (http, dotenv, secure_storage, svg, intl, fl_chart, firebase_core/messaging, google_sign_in, sign_in_with_apple; dev: integration_test, mockito, build_runner, flutter_lints); type-based `lib/` folders (`config i18n models[+responses] screens services[+api] styles widgets`); `AppTheme.themeFor` (dark + light × 4 accent palettes from `tokens.css`); `AppLocalizations` + `en.json` + `pt.json`; `main.dart` shell with 5-tab `NavigationBar` (Feed/Train/Evolve/Diet/Me) via `PlaceholderScreen`. Verified: `flutter pub get` resolves, `flutter analyze` clean (no issues), `flutter test` passes the smoke widget test.
  - **AH-010 WIP** — Flyway migration `V20260527130000__create_users_roles_refresh_tokens.sql` written (tables: `users`, `user_roles`, `refresh_tokens` per 02-data-model §4.1, MVP-simplified — no `client_uuid`, no separate per-context schema; emails/handles normalized in the service rather than via `citext`). Build-verify shares the same JDK-25 unblock as AH-002/004.
- **2026-05-28** — Backend verified on JDK 25 + Docker.
  - `cd api && ./mvnw verify` → **BUILD SUCCESS** (19.5s, cache warm).
  - `SmokeIT` booted Spring Boot 4 (Java 25.0.1) against Testcontainers
    PostgreSQL 16, applied Flyway baseline + `V20260527130000` (users + roles
    + refresh_tokens), and asserted `/actuator/health` is `UP`.
  - Fix landed: Spring Boot 4 no longer exposes `TestRestTemplate` at
    `org.springframework.boot.test.web.client`, so `AbstractIntegrationTest`
    now uses a plain `RestTemplate` configured to not follow redirects and not
    throw on 4xx/5xx — exactly lotuga's pattern.
  - **AH-002, AH-004, AH-010 → DONE.** Epic 0 fully closed.
  - **AH-011 DONE.** `User` entity + `UserRepository`; `AuthService.register`
    normalizes email/handle to lowercase, hashes with BCrypt, grants `ATHLETE`
    role; `POST /api/auth/register` → `201` + `UserDto`; duplicate email or
    handle → `409` (new `ConflictException` + handler) with stable
    `MessageCode` (EMAIL_ALREADY_REGISTERED / HANDLE_ALREADY_TAKEN);
    `GET /api/me` returns the authenticated profile (testable once AH-012
    wires the JWT filter). Verified: `AuthServiceTest` 3/3 (Mockito);
    `RegisterIT` 4/4 (Testcontainers + Flyway: happy, 409 dup-email
    case-insensitive, 409 dup-handle, 400 validation).
  - **Latent fix discovered while landing AH-011:** Spring Boot 4 split
    auto-configuration into per-feature modules. Without
    `org.springframework.boot:spring-boot-flyway`, `FlywayAutoConfiguration`
    isn't present and migrations *never* run. The previous "verification" of
    AH-002/004/010 was actually only proving Spring Boot starts — SmokeIT
    only hits `/actuator/health` and so didn't notice. The new dep is now in
    `api/pom.xml`; Flyway logs confirm both migrations apply on every test
    boot. **Lesson:** every Boot 4 feature needs its `spring-boot-<feature>`
    module pulled in (most starters do this transitively; raw library deps
    like `flyway-core` do not).
  - **API path convention (mirroring lotuga):** `/api/auth/...` and `/api/me`
    (no `/v1` prefix yet); 04-api-design.md's example `/v1` paths are
    deferred until we actually need versioning.
  - **AH-012 DONE.** Login + JWT issuance, full `security/` infrastructure
    (`JwtUtil`, `UserPrincipal`, `CustomUserDetailsService`,
    `JwtAuthenticationFilter`), real `SecurityConfig` wiring
    (`DaoAuthenticationProvider`, `AuthenticationManager`, explicit
    `AuthenticationEntryPoint` + `AccessDeniedHandler` writing the `ApiResponse`
    envelope on 401/403). Refresh tokens: `RefreshToken` entity +
    `RefreshTokenRepository` + `RefreshTokenService` (SecureRandom + SHA-256
    hash; never store the plain value). DTOs: `LoginRequest`, `AuthResponse`
    (accessToken, refreshToken, accessTokenExpiresIn, tokenType=Bearer, user).
    Wrong-password / unknown-email surface as a domain
    `InvalidCredentialsException` (not Spring's `BadCredentialsException`),
    handled by the global advice with a stable `INVALID_CREDENTIALS` code.
    `LoginIT` 6/6: happy path with persisted refresh-token hash, case-
    insensitive email, 401 wrong password, 401 unknown email (no enumeration),
    401 `/api/me` without token, 200 `/api/me` with valid access token.
  - **Footnote on a sharp edge:** `AbstractIntegrationTest` had to switch
    from `SimpleClientHttpRequestFactory` to `JdkClientHttpRequestFactory` —
    the older factory uses `HttpURLConnection`, whose built-in HTTP-auth state
    machine quietly consumes the body of 401 responses, making it look as if
    the server returned an empty 401. Real HTTP clients (Flutter's `http`
    package, browsers, curl) don't have this quirk; only the test harness was
    affected. Also added the `spring-boot-flyway` module (Spring Boot 4
    modularized auto-configuration per feature).
  - **AH-013 DONE.** Refresh-token rotation, reuse detection, logout.
    `RefreshTokenService.rotate` validates + revokes the presented token and
    issues a fresh one; presenting an already-revoked token is treated as
    compromise — every active token for that user is revoked before throwing.
    `RefreshTokenService.revoke` is idempotent. Endpoints:
    `POST /api/auth/token/refresh` returns a full `AuthResponse`;
    `POST /api/auth/logout` returns 204. `RefreshIT` 6/6: happy rotation +
    revocation, reuse-detection compromise revokes all active tokens, expired
    token 401, unknown token 401, logout revokes + blocks subsequent refresh,
    logout with unknown token is 204 idempotent. Two sharp edges fixed:
      • JWT access tokens now carry a `jti` (UUID) — without it, login + refresh
        in the same millisecond produced byte-identical tokens.
      • `AuthService.refresh` uses `@Transactional(noRollbackFor =
        InvalidRefreshTokenException.class)`. With default REQUIRED propagation
        the inner `rotate`'s rollback rules are ignored; without this on the
        outer, the reuse-detection revocations would roll back.
  - **AH-014 DONE.** Password reset via email. New `password_reset_tokens`
    schema (Flyway `V20260528120000`). `PasswordResetService` issues 6-char
    hex codes (SHA-256-hashed at rest, plain value emailed and discarded),
    `consumeCode` enforces single-use + 15-min expiry — unknown / used /
    expired all collapse into one `INVALID_RESET_CODE` (so brute-forcing
    learns nothing). `EmailService` wraps `JavaMailSender` (plain text MVP).
    `AuthService.forgotPassword` never reveals whether the email exists
    (always 202, mail errors logged + swallowed). `AbstractIntegrationTest`
    now starts GreenMail on the standard test SMTP port (3025) and resets
    its mailbox per test; `application-it.properties` overrides
    auth/STARTTLS to off so the in-process server can accept. `PasswordResetIT`
    5/5: happy path reads the code straight out of the mailbox and flips the
    password; unknown-email returns 202 with zero emails sent; same-code
    reuse, unknown code, and expired code each return 400 +
    `INVALID_RESET_CODE`. Two sharp edges:
      • `app.mail.from` defaulted to empty string in `application.properties`
        (`${MAIL_FROM:}`) — `JavaMailSender` then can't parse the From and
        raises `MailParseException`. Sensible default is now
        `noreply@athletehub.app`.
      • `spring.mail.properties.mail.smtp.starttls.required=true` is the
        right prod setting, but GreenMail doesn't negotiate STARTTLS — the
        `it` profile overrides auth + STARTTLS to off.
  - **Next:** AH-015 (OAuth2 social login — Google + Apple).
