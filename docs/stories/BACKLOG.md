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
| AH-015 | Social login (Apple, Google) OAuth2 | DONE | AH-012 |
| AH-016 | Role switch (athlete/coach) + profile update | DONE | AH-011 |
| AH-017 | Client: auth screens, secure storage, http_interceptor, auth_api_service | DONE | AH-012, AH-003 |

### EPIC 2 — Social graph & profile · [epic-2-social-profile.md](epic-2-social-profile.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-020 | Schema: follows, user_counters | DONE | AH-010 |
| AH-021 | Follow/unfollow, followers/following | DONE | AH-020 |
| AH-022 | Find people (search) + suggestions | DONE | AH-021 |
| AH-023 | Public profile aggregate endpoint | DONE | AH-021 |
| AH-024 | Client: Find People + Profile screens, follow button | DONE | AH-023, AH-017 |

### EPIC 3 — Training · [epic-3-training.md](epic-3-training.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-030 | Schema: exercises, templates, sessions, session_exercises, sets, cardio, PRs | DONE | AH-010 |
| AH-031 | Exercise catalog endpoints + seed | DONE | AH-030 |
| AH-032 | Today's plan + start session | DONE | AH-031 |
| AH-033 | Log/complete sets + finish session (volume, PR detection) | DONE | AH-032 |
| AH-034 | Cardio logging (run/walk/cycle) | DONE | AH-030 |
| AH-035 | Recent sessions + weekly cardio summary | DONE | AH-033, AH-034 |
| AH-036 | Client: Train, live Workout (rest timer), Cardio screens + service | DONE | AH-033, AH-017 |

### EPIC 4 — Body / Evolution · [epic-4-body-evolution.md](epic-4-body-evolution.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-040 | Schema: evaluations, evaluation_measurements | DONE | AH-010 |
| AH-041 | Save evaluation + measurements + body-fat computation | DONE | AH-040 |
| AH-042 | Body overview + metric series (weight/arm/waist/bench, ranges) | DONE | AH-041 |
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

**Progress:** 27 done / 47. **Epic 1–3 fully closed; Epic 4 backend done (3/4) — only AH-043 (Flutter client) remains to close the epic.**

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
  - **AH-015 DONE.** OAuth2 social login (Google + Apple), mobile-app-driven:
    the app does the native OAuth dance and POSTs the resulting ID token to
    `/api/auth/oauth/{google|apple}`; the backend verifies and issues our own
    token pair. New schema (`oauth_accounts`, Flyway `V20260528130000`) with
    `(provider, provider_uid)` UNIQUE. `OAuthTokenVerifier` interface +
    `NimbusOAuthTokenVerifier` impl that builds one `NimbusJwtDecoder` per
    provider against the JWKS URI (lazy fetch + cache) and validates
    issuer + audience after decoding. `SocialAuthService.loginWithProvider`:
    existing `oauth_account` → reuse user; known email → link the existing
    user; unknown → create user (ATHLETE role, no `password_hash`, handle
    derived from email's local part with `_N` collision suffix). `SocialAuthIT`
    6/6 (mocks the verifier with `@MockitoBean` — no WireMock + stub JWKS
    needed): new-user create + link; repeat login reuses user; existing-email
    link; invalid token → 401; unsupported provider in path → 401;
    handle-collision falls through to `<base>_1`. One sharp edge:
    `OAuthAccount.user` is `@ManyToOne(LAZY)`, so reading `.getEmail()` from a
    detached entity in the test throws `LazyInitializationException` — compare
    by id instead (the id lives on the proxy itself).
  - **AH-016 DONE.** `UserService` + a richer `UserController`: `GET /api/me`
    (existing), `PATCH /api/me` (partial profile update — fullName, bio, age,
    heightCm, avatarHue, with Bean Validation bounds), `POST /api/me/roles/switch`
    (grants the role on first use, which is the design's "explicit upgrade"
    path; the active-role choice itself lives in the client). `ProfileIT` 6/6:
    partial PATCH leaves untouched fields alone, invalid values → 400,
    unauthenticated 401 on both PATCH and switch, switch to COACH grants the
    role and persists it, switch to ATHLETE is a no-op when already an
    athlete.
  - **AH-017 DONE.** Flutter auth flow lights up the backend.
    `SecureStorageService` (Keychain / EncryptedSharedPrefs) holds access +
    refresh + cached user JSON. `HttpInterceptor` attaches `Authorization:
    Bearer …`, on 401 with a token does a single refresh-and-retry via raw
    `http` (no recursion), and on refresh failure clears local state and
    fires a global `onUnauthorized` callback so `main.dart` can route to
    login. `AuthApiService` covers register / login / oauth / logout / forgot /
    reset and throws `ApiException(code, message, fieldErrors)` on non-2xx
    so screens can map stable codes to localized strings. Screens:
    `LoginScreen` (combined sign-in / create-account with a SegmentedButton,
    matching the original `SignInScreen` handoff — gradient logo, "Train.
    Track. Evolve." headline with accent on "Evolve.", form-level error
    text); `ForgotPasswordScreen`; `ResetPasswordScreen` (6-char hex pattern
    matching backend issuance). `main.dart` got a `navigatorKey` (used by
    `HttpInterceptor.onUnauthorized`) and an `_AuthGate` that reads
    `hasSession()` on cold start and routes to `MainShell` or `LoginScreen`.
    OAuth social buttons render but stay disabled until the native flow
    lands. Verified: `flutter analyze` clean (no issues); `flutter test` 2/2
    green (login renders sign-in mode with email + password; toggling to
    create-account reveals full name + handle). Two sharp edges:
      • `pumpAndSettle` doesn't terminate while a `CircularProgressIndicator`
        animates — the original cold-start widget test (booting via `main()`
        through the auth-gate spinner) hung. Test the screens directly;
        leave end-to-end boot to `integration_test/`.
      • `flutter_secure_storage` calls native plugin code that isn't wired in
        the unit-test environment, so anything that touches the gate
        belongs in `integration_test/`, not `test/`.
  - **Epic 1 is fully closed.** All eight stories (AH-010..017) are DONE; full
    athlete-auth API + Flutter auth flow + token persistence + automatic
    refresh + four screens.
  - **AH-020 + AH-021 DONE.** Social graph foundation. New schema (Flyway
    `V20260528140000`): `follows` (surrogate id PK + `(follower_id, followee_id)`
    UNIQUE + `CHECK (follower_id <> followee_id)` + indexes on
    `(followee_id, id DESC)` and `(follower_id, id DESC)` for cursor scans);
    `user_counters` (denormalized followers/following/sessions/posts, backfilled
    for existing users + auto-inserted on register and OAuth signup). `Follow`
    + `UserCounters` entities; `FollowRepository` with derived
    `findByFollowerIdAndFolloweeId` + `@Modifying` delete + cursor JPQL;
    `UserCountersRepository` with atomic `adjustFollowers`/`adjustFollowing`
    UPDATEs. `FollowService` handles idempotent follow + unfollow inside one tx
    (counter never moves twice for a no-op call) and exposes
    `listFollowers`/`listFollowing` with cursor pagination. Endpoints on
    `UserController`: `POST /api/users/{id}/follow`, `DELETE
    /api/users/{id}/follow`, `GET /api/me/followers?cursor&limit`, `GET
    /api/me/following?cursor&limit` (limit clamped 1–100). `FollowIT` 6/6:
    follow + unfollow + idempotent re-follow + idempotent re-unfollow with
    counters consistent; cannot follow yourself (400); unknown target (404);
    unauthenticated (401); followers/following list reflects the graph; cursor
    pagination walks pages and reports no-more correctly. One sharp edge:
    naive "items == limit → next page exists" can't distinguish "exactly limit,
    last page" from "more remain" — fetch `limit + 1`, the extra row is the
    has-more signal. `CursorPage<T>` and `PublicUserDto` (public-safe shape:
    id, fullName, handle, avatarHue, bio) introduced as reusable envelopes.
  - **AH-022 + AH-023 DONE.** Find people (search + suggestions) and public
    profile aggregate.
    `GET /api/users/search?q&cursor&limit` — case-insensitive partial match
    on `full_name` and `handle` (LOWER(...) LIKE LOWER(CONCAT('%', :q, '%'))),
    excludes self, empty query → empty page, cursor walks id-DESC.
    `GET /api/users/suggestions?cursor&limit` — users I don't follow yet,
    each annotated with a `mutualCount` (users I follow that the candidate
    also follows, via a correlated JPQL subquery — no extra round trips, fed
    by a `JpaRepository @Query` returning the `SuggestedUserDto` constructor
    expression directly).
    `GET /api/users/{handle}` — `PublicProfileResponse { user, followers,
    following, iFollow }` reads from `user_counters` so a profile open is
    one row + one follow check; `iFollow` is forced false when the viewer
    asks for their own profile.
    `SearchAndProfileIT` 10/10: partial-name + partial-handle search excludes
    self, empty `?q=` returns an empty page, 401 without a token; suggestions
    exclude self + already-followed; mutual count is correct for the AND of
    two follow sets; profile reflects counters + iFollow true after follow,
    iFollow false otherwise, iFollow false for self, 404 on unknown handle.
    One sharp edge: `/api/users/{id}/follow` and `/api/users/{handle}` would
    overlap — the follow routes now use `{id:\\d+}` so a handle like
    `alice.lifts` reaches the profile endpoint, not a Long-conversion error.
  - **AH-024 DONE** — Flutter client: Find People + Profile screens +
    optimistic follow button. Models: `PublicUser`, `SuggestedUser`,
    `CursorPage<T>` (generic envelope), `PublicProfileResponse`. Service:
    `SocialApiService` with `search`, `suggestions`, `profileByHandle`,
    `follow`, `unfollow`, `myFollowers`, `myFollowing` — all go through
    `HttpInterceptor` so 401 → silent refresh + retry. Widgets: `Avatar`
    (initial-based circle tinted by `avatarHue` HSL), `FollowButton`
    (optimistic flip with revert on `ApiException`, SnackBar on error,
    filled when not following / outlined when following).
    Screens: `FindPeopleScreen` (250 ms-debounced search field; loads
    suggestions when empty, switches to search results when not; shared
    `_UserRow` with avatar + name + subtitle + follow button; row tap
    pushes ProfileScreen) and `ProfileScreen` (header with avatar +
    fullName + @handle + bio, counters row Following/Followers/Sessions,
    follow button hidden when viewing own profile; pull-to-refresh; the
    follow-toggle callback adjusts the local followers counter in
    real-time). Wired `MainShell`: Me tab renders `ProfileScreen` with
    cached user's handle (via new `SecureStorageService.getCachedUser()`),
    and the Feed tab AppBar has a `person_search_outlined` action that
    opens FindPeopleScreen.
    `flutter analyze` clean (no issues); `flutter test` 2/2 green.
    **Closes Epic 2 (5/5 stories).** Sessions counter on profile renders
    a placeholder `—` until AH-036 surfaces it.
  - **AH-030 DONE** — Flyway `V20260528150000__create_training_tables.sql`
    creates the 8 training tables per `02-data-model.md §4.4` with the
    MVP simplifications spelled out: no `client_uuid` (online-first),
    no `cardio_samples` hypertable (LATER), no PostGIS `route`
    geography (LATER), and no `assignment_id` FK yet (the `assignments`
    table arrives in Epic 7 — the `source = 'assigned'` enum value is
    already accepted so the data shape stays forward-compatible).
    Sharp edges encoded as CHECKs: exercises XOR rule (a row is either
    global with `created_by IS NULL` or owned with `created_by NOT
    NULL`); workout_sessions status ∈ {in_progress, completed,
    abandoned}; personal_records UNIQUE(user_id, exercise_id, metric)
    so "current PR" is a single row; cardio_activities type ∈ {run,
    walk, cycle} and source ∈ {self, assigned, import}; rpe ∈ [0..10];
    HR ∈ (0..300); set_number ≥ 1; positions ≥ 0. Cascade choices:
    deleting a workout_session cleans its session_exercises + sets;
    deleting a workout_template nulls workout_sessions.template_id
    (sessions outlive their plan); deleting an exercise is RESTRICTed
    when a session uses it (catalog can't drop out from under history).
    Indexes documented in the spec are all present:
    `idx_workout_sessions_user_started`, `idx_cardio_activities_user_started`,
    plus a partial `idx_workout_sessions_user_active` for "what's
    running right now?" lookups, `idx_exercises_name_lower` for case-
    insensitive search, and per-template/per-session position indexes.
    `TrainingSchemaIT` 9/9 (135 ms): table presence, index presence,
    XOR constraint enforced, status / type / source enums reject bad
    values, PR uniqueness, session→exercises/sets cascade, exercise
    delete RESTRICT, template delete SET NULL. Full backend suite still
    green: **62/62** (10 ITs + 3 unit). `assignment_id` and the FK
    constraint will land alongside the assignments table in Epic 7.
  - **AH-031 DONE** — exercise catalog. Flyway seed migration
    `V20260528160000__seed_global_exercises.sql` loads 34 global
    exercises across 4 categories (push / pull / legs / core) with
    `is_global = true` and `created_by = NULL` — the XOR constraint
    from AH-030 keeps customs and globals from leaking into each other.
    JPA `Exercise` entity + `ExerciseRepository` + `ExerciseService` +
    `ExerciseController` + `CreateExerciseRequest` + `ExerciseDto` +
    new `EXERCISE_ALREADY_EXISTS` message code.
    Endpoints (auth required, all under existing `/api`-no-`v1`
    convention — see deviation note below):
      * `GET /api/exercises?q=&cursor=&limit=` — globals + caller's
        own customs only; substring match on lower(name); cursor on
        id ASC with the `limit + 1` trick.
      * `POST /api/exercises` — stamps `is_global = false`,
        `created_by = caller`; rejects duplicate names per-user
        (case-insensitive) but allows naming a custom after a global
        (intentional — users can fork a catalog lift with their own
        notes/equipment).
    Sharp edges encountered + fixed:
      * **Postgres can't type-infer `null` inside `CONCAT('%', :q, '%')`** —
        it lands on `lower(bytea)` which doesn't exist. Split the
        repository into `searchVisible` (no name filter) and
        `searchVisibleByName` (with one) so we never pass null for
        `:q`. Cleaner than casting or COALESCE-ing in JPQL.
      * `DefaultUriBuilderFactory` in tests re-encodes pre-encoded
        URL params (`%20` → `%2520`), so the test helper now uses
        URI templates + a vars map and lets the builder do the
        encoding once.
    `ExerciseCatalogIT` 10/10: seed loaded ≥30 globals, 401 without
    auth, case-insensitive substring match, blank query returns full
    catalog (whitespace-only trimmed to "no filter"), cursor walks
    pages without overlap, custom is owner-visible only, same-name
    duplicate within user → 409, same name across users → both 201,
    custom can shadow a global name, blank name → 400 with
    `VALIDATION_FAILED`. Full suite: **72/72** (11 ITs + 3 unit).
    **Path convention deviation:** epic spec says `/api/v1/exercises`
    but every endpoint in this codebase is unversioned, so we kept it
    `/api/exercises`. When versioning eventually lands, it's an
    across-the-board change rather than one rogue family.
  - **AH-032 DONE** — today's plan + start session. New Flyway
    migration `V20260529120000__create_template_schedules.sql` adds a
    `template_schedules(id, template_id, day_of_week)` table with
    `UNIQUE(template_id, day_of_week)` + `CHECK(day_of_week BETWEEN
    1 AND 7)`. ISO weekday numbering (Mon=1…Sun=7) matches Java's
    `DayOfWeek.getValue()` so the API doesn't need a translation
    table. Per-template (not per-user) — templates already have
    `owner_id`, so visibility is derived through the join. When Epic
    7 adds coach `assignments`, the today endpoint will UNION the two
    sources.
    Entities (5): `WorkoutTemplate`, `WorkoutTemplateExercise`,
    `WorkoutSession`, `SessionExercise`, `TemplateSchedule`. Repos
    for each, with `WorkoutTemplateRepository.findScheduledFor` doing
    the `templates ⨝ schedules WHERE owner_id = ? AND day_of_week =
    ?` join. DTOs: `TodayPlanResponse {template?, activeSessionId?}`
    (the four nullable combinations are all real states —
    rest-day/ready/yesterday-still-open/plan-but-active), plus
    `WorkoutTemplateDto`, `TemplateExerciseDto`, `WorkoutSessionDto`,
    `SessionExerciseDto`, `StartSessionRequest`.
    Endpoints:
      * `GET /api/training/today` — joins to find today's template
        (sorted by schedule id ASC, take first if multiple), reads
        the user's active in-progress session id, hydrates exercise
        names in one batch.
      * `POST /api/workout-sessions` — rejects if an in-progress
        session already exists (409 `ACTIVE_SESSION_EXISTS`); rejects
        an unknown or another-user's template (404 `TEMPLATE_NOT_FOUND`);
        creates the session and seeds `session_exercises` from the
        template's slots in one transaction. `target_weight` left
        null — we don't try to parse "80 kg" into a numeric; the user
        enters real per-set weights anyway.
    Cross-row "at most one in_progress per user" rule lives in the
    service, not the schema (a CHECK across rows isn't practical in
    standard SQL). The partial index `idx_workout_sessions_user_active`
    from AH-030 keeps that lookup cheap.
    Testability: new `Clock` bean in `WebConfig` so
    `TrainingTodayAndStartIT` can pin "today" via a fixed-clock
    `@TestConfiguration` — the test always sees Wed 2026-05-27 so
    weekday-dependent assertions aren't flaky on CI.
    `TrainingTodayAndStartIT` 11/11 (2.7 s): rest day, planned
    template returns full exercises in order, today ignores templates
    scheduled on other days, today ignores another user's
    today-scheduled template, today reports active session id when
    one is in progress; start without template → empty session,
    start with template → seeded in order with names, double-start
    → 409, unknown template → 404, another user's template → 404,
    no token → 401. Full suite: **83/83** (12 ITs + 3 unit).
  - **AH-033 DONE** — granular set ops + finish session with PR
    detection. Two endpoints:
      * **`PATCH /api/workout-sessions/{id:\\d+}`** — body is
        `{ sets: [SetOpRequest, ...] }` (1..100 ops). Each op is
        idempotent on the natural key `(sessionExerciseId, setNumber)`:
        `upsert` inserts if no row matches and updates if one does;
        `delete` drops the matching row (no-op on miss). When a set
        transitions from done=false to done=true we stamp
        `completed_at = now`; the reverse clears it. Atomic per
        request — either every op succeeds or none do. Returns the
        full updated `WorkoutSessionDto`.
        Chose granular ops over diff-replace because a flaky network
        during a workout costs at most the in-flight set, not the
        whole session.
      * **`POST /api/workout-sessions/{id:\\d+}/finish`** — server
        recomputes the authoritative rollups (`total_sets`,
        `total_volume_kg = SUM(weight * reps)` over done sets) regardless
        of any running client estimate; detects per-exercise PRs on
        two metrics:
          - **e1RM (Epley)** = `weight * (1 + reps / 30)` — best across
            done sets per exercise.
          - **max_weight** — heaviest done set per exercise.
        Loads existing PRs in one batch
        (`findByUserIdAndExerciseIdIn`), compares, upserts only when
        beaten (so re-finishing the same numbers doesn't churn rows),
        flags the responsible set's `is_pr = true`, increments
        `user_counters.sessions`, sets `ended_at` + `duration_seconds`,
        flips `status → completed`.
    Sharp edges encoded:
      * Cross-row "at most one in_progress per user" stays in service
        code (no portable SQL CHECK).
      * Patch + finish reject other users' sessions with 404
        `SESSION_NOT_FOUND` (don't disclose existence); patch ops with
        a `sessionExerciseId` foreign to the session → 400
        `INVALID_SET_OP`; patch/finish on completed → 409
        `SESSION_NOT_IN_PROGRESS`.
      * Unknown op string → 400 `INVALID_SET_OP`.
      * Sets with `done = false` or null `weight_kg`/`reps` are
        ignored in rollups + PR detection (so a 500 kg phantom set
        left at done=false can't accidentally PR you).
      * BigDecimal scale stabilized at 2 for both rollups and PRs so
        comparison/round-trip stays clean.
      * On the BigDecimal side, used `setScale(2, HALF_UP)` everywhere
        on the response path so JSON ↔ DB ↔ in-memory stays
        round-trip stable.
    `LogSetsAndFinishIT` 16/16 (5 s): patch upsert insert-then-update
    on same key, delete then re-delete (idempotent), multi-op
    atomicity across two session_exercises, cross-session se_id → 400,
    patch on another user's session → 404, patch on completed → 409,
    unknown op → 400; finish computes total_sets + total_volume
    correctly, creates e1rm + max_weight PRs when none existed and
    flags the set, doesn't create PRs when prior is better, flags
    different sets for different metrics when each wins one,
    increments `user_counters.sessions`, ignores undone sets, finish
    on completed → 409, finish on another user's session → 404,
    finish without token → 401.
    Full backend suite: **99/99** (13 ITs + 3 unit).
  - **AH-034 DONE** — cardio logging. JPA `CardioActivity` entity
    over the existing AH-030 table; no schema changes. Two endpoints:
      * `POST /api/cardio-activities` — body has required
        `{type, distanceM, durationSeconds}` plus optional
        `{avgPaceSPerKm, avgPowerW, avgHr, maxHr, elevationGainM,
        kcal, notes, startedAt}`. Bean validation mirrors the schema
        CHECKs: type ∈ {run, walk, cycle}, distance/duration ≥ 0,
        HR ∈ [1..299], pace/power/elevation ≥ 0 when supplied. Bad
        payloads → 400 `VALIDATION_FAILED` with field errors, not
        500 `DataIntegrityViolation`. Source hardcoded to `self` —
        `import` and `assigned` will route through dedicated
        endpoints (wearable sync; Epic 7 assignments).
        `startedAt` defaults to NOW() via `@PrePersist` when null so
        the client only needs it for backfill.
      * `GET /api/cardio-activities?cursor=&limit=` — newest-first
        cursor pagination on id DESC (same `limit + 1` pattern).
        Surrogate-id cursor (not started_at) so backfilled activities
        land at the top of the list — matches the UX of "I just
        logged my Sunday run on Tuesday".
    Files: `model/CardioActivity`, `dto/{CardioActivityDto,
    CreateCardioRequest}`, `repository/CardioActivityRepository`,
    `service/CardioService`, `controller/CardioController`.
    `CardioActivityIT` 11/11 (3 s): full payload roundtrip, minimal
    payload (server defaults startedAt + source), reject unknown
    type → 400, reject HR > 299 → 400, reject negative distance →
    400, no token → 401; list empty when no activities, newest-first
    ordering across three types, no leakage between users, cursor
    walks pages, list without token → 401.
    Full backend suite: **110/110** (14 ITs + 3 unit).
  - **AH-035 DONE** — recent sessions + weekly cardio summary.
    Closes the **backend half of Epic 3** — only AH-036 (Flutter
    client) remains. Two endpoints:
      * **`GET /api/workout-sessions?cursor=&limit=`** — newest-first
        cursor pagination on id DESC. Both `in_progress` and
        `completed` sessions surface; the client filters if it only
        wants completed (matches reality: "what just happened" is
        what users want to see). Returns a slim
        `WorkoutSessionSummaryDto` (no exercises/sets) so a 20-row
        page stays cheap — full hydrated view will land alongside
        a single-session GET endpoint when needed.
      * **`GET /api/training/weekly-summary`** —
        `{thisWeekKm, lastWeekKm, deltaKm}`, all `BigDecimal(2)`.
        ISO weeks (Mon 00:00 … next Mon 00:00) in the server's
        default zone via the `Clock` bean. Week boundaries computed
        with `TemporalAdjusters.previousOrSame(MONDAY)` so "today
        is Monday" resolves to this week's start (not the previous
        one). Negative delta when this week is lower —
        `deltaKm = thisKm − lastKm`, signed.
    New: `WorkoutSessionSummaryDto`, `WeeklySummaryDto`,
    `WorkoutSessionRepository.findRecent`,
    `CardioActivityRepository.sumDistanceBetween` (COALESCE to 0 so
    callers don't null-check). `TrainingService` grew
    `listRecentSessions` + `getWeeklySummary` + a tiny `metersToKm`
    helper at scale 2 HALF_UP. `TrainingController` got the two
    new endpoints; clamp/principal helpers stay shared.
    `RecentSessionsAndWeeklySummaryIT` 12/12 (4 s) using the same
    pinned `Clock` `@TestConfiguration` pattern as AH-032 (Wed
    2026-05-27 → this week starts Mon 2026-05-25):
      * recent list — empty case, newest-first w/ in-progress
        included, per-user visibility, cursor pagination, summary
        DTO omits `exercises` field, no token → 401.
      * weekly — zero when no cardio, sums correctly into km with
        delta vs last week, negative delta when this is lower,
        ignores activities outside the two-week window (two-weeks-
        ago + future), no leakage between users, no token → 401.
    Full backend suite: **122/122** (15 ITs + 3 unit).
    **MVP timezone note:** weekly bucketing uses the server's
    default zone. A future timezone-aware version reads the user's
    profile zone and recomputes the Monday boundary. For MVP this
    is acceptable — every user sees a stable weekly window relative
    to the server.
  - **AH-036 DONE** — Flutter Train / Workout / Cardio. **Closes
    Epic 3.** Backend gap fixed first: added `GET
    /api/workout-sessions/{id:\\d+}` (full hydrated session) so the
    live-workout screen has something to load on Resume — reuses
    the existing `hydrateSession` helper in `TrainingService`.
    `LogSetsAndFinishIT` grew 2 cases → **18/18**, full suite
    **124/124**.
    Client work:
      * **9 models** mirroring the backend DTOs: `TemplateExercise`,
        `WorkoutTemplate`, `TodayPlanResponse`, `ExerciseSet`,
        `SessionExercise`, `WorkoutSession`, `WorkoutSessionSummary`,
        `WeeklySummary`, `CardioActivity`. Manual `fromJson` per
        CONVENTIONS (no codegen).
      * **`services/api/training_api_service.dart`** — `today()`,
        `weeklySummary()`, `recentSessions()`, `getSession()`,
        `startSession()`, `patchSession()`, `finishSession()`,
        `listCardio()`, `createCardio()`. All through
        `HttpInterceptor` so 401 → silent refresh + retry.
      * **`screens/train_screen.dart`** — three-state hero card
        keyed off `TodayPlanResponse`:
          - `activeSessionId != null` → "Resume" CTA
          - `template != null && no active` → "Start <name>" + chip
            row of exercises
          - both null → "Rest day" + freestyle CTA
        Weekly cardio bar chart via `fl_chart` (this week vs last
        week) with a signed delta chip. Recent-sessions list (up to
        10) reads the slim summary endpoint. AppBar action opens
        CardioScreen. Pull-to-refresh re-fetches all three calls
        in parallel via `Future.wait`.
      * **`screens/workout_screen.dart`** — live workout. Per-set
        rows have inline weight + reps fields, a "done" checkbox,
        and a trash icon. Each interaction queues a granular PATCH
        op (`upsert`/`delete`); ops are coalesced so an over-eager
        tap doesn't fan out 5 in-flight requests; on `ApiException`
        we re-`GET` the session to reconcile. Tapping "done" starts
        a **client-side rest timer** (90 s default; skip button on
        the chip). Header shows running volume + done/total set
        ratio + a progress bar. Finish posts to backend then shows
        a bottom-sheet summary (total volume, sets, PR count,
        duration formatted mm:ss); PR-flagged sets get a trophy
        icon when the server returns the flagged DTO.
      * **`screens/cardio_screen.dart`** — segmented run/walk/cycle
        picker, distance (km) + duration (min) required, optional
        avg/max HR, elevation, kcal, notes. Distance is converted
        to metres + duration to seconds before POSTing so the
        backend speaks its own unit. Per-field error map from the
        backend's `VALIDATION_FAILED` envelope surfaces inline.
      * **`main_shell.dart`** — Train tab now hosts `TrainScreen`
        (replaces placeholder).
    `flutter analyze` clean; `flutter test` 2/2.
    Design choices captured:
      * **PATCH op coalescing** in `WorkoutScreen` — a single
        `_patching` flag plus a `_pendingOps` queue means rapid
        taps batch into one request; the loop drains the queue
        before clearing the flag.
      * **Optimistic UX**: the set row's text fields and checkbox
        update local state, then queue the op; the server's
        returned `WorkoutSession` becomes the source of truth on
        response. `_SetRow.didUpdateWidget` reflects server-side
        changes that aren't the user's own typing.
      * **Rest timer is purely client-side** per the epic spec — no
        server endpoint, no persistence across app kills.
      * Three-state hero card derives entirely from the
        `TodayPlanResponse` shape, no extra client state.
  - **AH-040 DONE** — Body / Evolution foundation. Flyway migration
    `V20260529130000__create_evaluations_tables.sql` creates two
    tables per `02-data-model.md §4.5` with the MVP simplifications
    spelled out in the migration header: no `client_uuid` (online-
    first), no `body_metric_samples` hypertable (LATER — daily
    weight from wearables ships with Epic 9 sync), no
    `assigned_by_coach_id` / `eval_request_id` FKs yet (Epic 7's
    `assignments` + `eval_requests` tables haven't landed; the
    `source = 'coach'` enum value is already accepted so the data
    shape stays forward-compatible).
    Tables
      * **`evaluations`** — `weight_kg` required (every assessment
        captures it); `body_fat_pct` + `bf_method` paired (a body-
        fat % needs to record how it was computed); both nullable
        so a "weight-only check-in" is a legal row. `source ∈
        {self, coach}`.
      * **`evaluation_measurements`** — one row per
        `(evaluation_id, point_id)`. `point_id` is a free-form TEXT
        like `'neck'`, `'chest'`, `'arm_r'`, `'tricep'`, `'suprail'`
        — new measurement points (a new skinfold site, a per-thigh
        circumference) don't need a migration. `kind ∈
        {circumference, skinfold}`; `unit ∈ {cm, mm}`. `UNIQUE
        (evaluation_id, point_id)` — re-measuring a point is an
        UPDATE, not an append.
    Sharp edges encoded as CHECKs:
      * **Body-fat XOR:** a row has both `body_fat_pct` + `bf_method`
        or neither — never one without the other.
      * `bf_method ∈ {jackson_pollock_7, durnin, navy, manual}` when
        present.
      * Range checks: `weight_kg ∈ [0, 1000)`, `body_fat_pct ∈
        [0, 100]` when present, `measurement.value ≥ 0`.
    Cascade choices:
      * `evaluations.user_id → ON DELETE CASCADE` (a user's
        evaluations die with them).
      * `evaluation_measurements.evaluation_id → ON DELETE CASCADE`
        (delete an evaluation, lose its measurements).
    Indexes documented in the spec are both present:
    `idx_evaluations_user_evaluated (user_id, evaluated_at DESC)`
    for the Evolution timeline + metric-series graphs, and
    `idx_evaluation_measurements_eval` so the "load evaluation +
    its measurements" join is one step out without scanning.
    **Derived graphs note:** the weight / arm / waist / bench 1RM
    series (ranges 4w/12w/6m/1y) are derived at read time from
    `evaluations + evaluation_measurements` (and `personal_records`
    for bench 1RM) — no "graph" table needed.
    `EvaluationsSchemaIT` 10/10 (110 ms): tables + indexes present,
    `bf_method` CHECK rejects unknown, body-fat ↔ method XOR enforced
    in both directions and both legal shapes (weight-only / fully
    populated), `source` CHECK rejects unknown ('import') and accepts
    'coach', weight + body-fat range CHECKs fire, measurement
    `kind` + `unit` CHECKs reject unknown values, `value ≥ 0`,
    UNIQUE per (eval, point) blocks dupes, evaluation → measurement
    cascade, user → evaluation cascade. Full backend suite: **134/134**
    (17 ITs + 3 unit).
  - **AH-040 follow-up: `users.sex` column.** New Flyway migration
    `V20260529140000__add_user_sex.sql` adds a nullable `users.sex`
    column with `CHECK (sex IS NULL OR sex IN ('male', 'female'))`.
    Nullable so existing users land with NULL; new users can omit
    at signup and add later via PATCH /me. Binary value set is a
    measurement constraint, not a social one — J-P / Durnin / Navy
    formulas have different equations per biological sex. A future
    social-gender feature would land on a separate column.
    Plumbed across the stack: `User` entity, `UserDto`,
    `SignupRequest` (+ optional `@Pattern`), `UpdateProfileRequest`,
    `AuthService.register` (passes through, null when omitted),
    `UserService.updateProfile` (null-means-leave-it). Client:
    `models/user_response.dart` round-trips it through secure
    storage + `/api/me`. `RegisterIT` +2 (round-trip; invalid → 400
    VALIDATION_FAILED), `ProfileIT` +1 (set + persist; invalid →
    400, prior value stays). Suite: 137/137.
  - **AH-041 DONE** — save evaluation + body-fat computation. Two
    endpoints:
      * **`POST /api/evaluations`** with body
        `{evaluatedAt?, weightKg, bfMethod?, bodyFatPct?, notes?,
        measurements: [...]}`. Three creation shapes are valid:
          1. **Weight-only** — `bfMethod` absent → row stored with
             `body_fat_pct` + `bf_method` both null; the schema XOR
             rule is satisfied. Measurements are still stored when
             supplied so the Evolution time-series graphs always
             have data.
          2. **Manual** — `bfMethod = "manual"` + `bodyFatPct`
             supplied → pass-through. Missing `bodyFatPct` → 400
             `BF_MANUAL_REQUIRES_PCT`.
          3. **Computed** — `bfMethod ∈ {jackson_pollock_7, navy}` →
             server computes from the user's profile + measurements
             via `BodyFatCalculator`. Missing required inputs →
             400 `BF_MISSING_MEASUREMENTS` (no chest skinfold for
             J-P 7) or `BF_MISSING_USER_FIELD` (sex/age/height not
             set on /me).
        `durnin` is reserved by the schema CHECK but the service
        rejects it with 400 `BF_METHOD_NOT_SUPPORTED` — Durnin &
        Womersley 1974 has age-bracket coefficients that bloat the
        code without much MVP value; lands in a follow-up.
        Duplicate `pointId` inside one payload → 400
        `VALIDATION_FAILED` (caught service-side so the user sees
        a friendly code instead of a 500 from the UNIQUE constraint).
      * **`GET /api/evaluations/{id:\\d+}`** — hydrated payload
        (evaluation + ordered measurements). 404
        `EVALUATION_NOT_FOUND` on someone else's row (don't
        disclose existence).
    Body-fat formula notes (encoded in `BodyFatCalculator`):
      * **Jackson-Pollock 7-site (Siri)** — requires the canonical
        7 skinfold points: chest, abdomen, thigh, tricep,
        subscapular, suprailiac, midaxillary (all `kind=skinfold,
        unit=mm`). Body density per sex/age, then `BF% = 495/BD −
        450`. Output clamped to [0, 100] and scale-2 to round-trip
        cleanly with `NUMERIC(5,2)`.
      * **Navy (Hodgdon-Beckett, cm form + Siri)** — male needs
        neck + waist; female needs neck + waist + hip; both need
        `users.heightCm`. Uses the **cm body-density form** fed
        straight into Siri:
        `BF% = 495 / D − 450`, where
        `D_male = 1.0324 − 0.19077·log10(waist−neck) +
        0.15456·log10(height)` and
        `D_female = 1.29579 − 0.35004·log10(waist+hip−neck) +
        0.22100·log10(height)`.
        **Sharp edge captured the first time around:** mixing the
        inch coefficients with cm inputs overshoots wildly (a
        normal-bodied woman read as ~52% body fat). The cm form
        above is calibrated for cm and produces sane numbers
        (~10% for the male sample, ~25% for the female sample).
        The class JavaDoc warns future-me.
    Files: `model/{Evaluation, EvaluationMeasurement}`,
    `repository/{Evaluation, EvaluationMeasurement}Repository`,
    `dto/{EvaluationDto, EvaluationMeasurementDto,
    CreateEvaluationRequest, EvaluationMeasurementRequest}`,
    `service/{EvaluationService, BodyFatCalculator}` (calculator
    is Spring-free for unit-test ergonomics),
    `controller/EvaluationController`, +5 `MessageCode` values.
    `EvaluationsIT` 17/17 (15 s): weight-only persists with bf
    fields null + still stores measurements when supplied; manual
    passes through + rejects missing pct; J-P 7 computes from
    skinfolds + age + sex + rejects missing skinfold + rejects
    missing user sex; Navy male computes correctly; Navy female
    requires hip and computes when given; Navy without heightCm
    → 400; Durnin → 400 BF_METHOD_NOT_SUPPORTED; unknown bfMethod
    → 400 VALIDATION_FAILED; duplicate pointId → 400; no token →
    401; GET own + GET another user's (404) + GET unknown (404).
    Full backend suite: **154/154** (18 ITs + 3 unit).
  - **Sidebar — exercise-search query split (touched in this commit
    if dirty).** The original `ExerciseRepository.searchVisible`
    JPQL wrapped `:q` in a `CONCAT('%', :q, '%')`; when the service
    passed `null` for an empty query, PostgreSQL's type inference
    landed on `lower(bytea)` which doesn't exist. Fix: split into
    `searchVisible` (no name filter) and `searchVisibleByName`
    (the LIKE branch). Service picks based on whether `q` is null.
    No behavior change for callers; tests still 10/10.
  - **AH-042 DONE** — recent evaluations list + metric series.
    **Closes the backend half of Epic 4** — only AH-043 (Flutter
    client) remains. Two endpoints:
      * **`GET /api/evaluations?cursor=&limit=`** — newest-first
        cursor pagination on id DESC (surrogate-id cursor, not
        `evaluated_at`, so backfilled rows surface at the top —
        same UX rule as cardio). Returns the slim
        `EvaluationSummaryDto` (no measurements) so a 20-row page
        stays cheap; the full hydrated view lives at
        `/api/evaluations/{id}` (AH-041).
      * **`GET /api/body/series?metric=...&range=...`** —
        `{metric, range, unit, points: [{at, value}, ...]}`.
        Points are sorted oldest → newest so the client renders
        straight into a line chart without re-sorting.
    Lives on a separate `BodyController` because it's a computed
    derivation across `evaluations + evaluation_measurements`
    rather than CRUD on a single evaluation row.
    Metric dispatch (in `EvaluationService.getMetricSeries`):
      * `weight` → reads `weight_kg` from each evaluation in the
        window. Unit: `"kg"`.
      * `body_fat` → reads `body_fat_pct`, filtering null rows
        (weight-only check-ins don't pollute the chart). Unit:
        `"%"`.
      * anything else → treated as a `point_id`. Joins through
        `evaluation_measurements` in one batched query
        (`findByEvaluationIdInAndPointId`); unit derived from the
        stored row (`cm` for circumferences, `mm` for skinfolds).
        Empty result → empty unit (we don't guess; the client
        knows what it asked for).
    Ranges accepted: `4w` (28 d), `12w` (84 d), `6m` (180 d),
    `1y` (365 d). Anything else → 400 `INVALID_RANGE` so we never
    run an unbounded scan. Window end is "now" via the existing
    `Clock` bean — tests pin it via the same
    `@TestConfiguration` pattern AH-032/035 established.
    **Bench 1RM history intentionally deferred.** The
    `personal_records` table only stores the *current* best per
    `(user, exercise, metric)`; reconstructing history needs a
    per-session scan that's out of MVP scope. The Train screen
    already surfaces PR count + flagged sets on the recent-sessions
    list. Worth a follow-up story when the UX needs it.
    Files:
      * `dto/{EvaluationSummaryDto, MetricPoint, MetricSeriesDto}`
      * `repository/EvaluationRepository` + `findRecent` +
        `findByUserInRange`
      * `repository/EvaluationMeasurementRepository` +
        `findByEvaluationIdInAndPointId`
      * `service/EvaluationService` + `listRecent`,
        `getMetricSeries`, `measurementSeries`, `parseRange`,
        `Clock` dep
      * `controller/EvaluationController` + GET list endpoint
      * `controller/BodyController` (new, `/api/body/series`)
      * `enums/MessageCode` + `INVALID_METRIC`, `INVALID_RANGE`
    `EvaluationListAndSeriesIT` 13/13 (5 s) using the same pinned
    `Clock` `@TestConfiguration` as AH-032/035 (Wed 2026-05-27):
      * list — empty case, newest-first ordering with summary DTO
        omitting `measurements`, per-user visibility, cursor
        pagination, no token → 401.
      * series — weight in 4w window includes only inside-window
        rows oldest-first; 12w window includes more history;
        body_fat filters null rows + uses `%` unit; point_id
        returns measurement values with stored unit (`cm`); empty
        point_id returns empty array + empty unit; no leakage
        between users; invalid range → 400 `INVALID_RANGE`; no
        token → 401.
    Full backend suite: **167/167** (19 ITs + 3 unit).
  - **Next:** **AH-043 — Flutter client: Evolution / New Evaluation /
    Graph detail.** Closes Epic 4. Roughly:
    `services/api/evaluation_api_service.dart` (today's overview
    via list + last-summary, create, get, list paginated, metric
    series), `screens/evolution_screen.dart` (latest weight + bf
    summary + chart picker + recent list), `screens/new_evaluation_screen.dart`
    (manikin-style body with point taps for circumferences /
    skinfolds, weight + bf-method form, calls POST /api/evaluations),
    `screens/graph_detail_screen.dart` (full metric series with
    range picker). Plain `setState`; online-first.
