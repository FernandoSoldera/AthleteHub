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
| AH-043 | Client: Evolution, New Evaluation (manikin), Graph detail + service | DONE | AH-042, AH-017 |

### EPIC 5 — Nutrition · [epic-5-nutrition.md](epic-5-nutrition.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-050 | Schema: foods, diet_plans, diet_meals, meal_items, diary_entries, favorites | DONE | AH-010 |
| AH-051 | Food DB search + seed + custom foods | DONE | AH-050 |
| AH-052 | Active diet plan + day endpoint (totals/remaining) | DONE | AH-050 |
| AH-053 | Diary entries (add food to a day) | DONE | AH-051, AH-052 |
| AH-054 | Client: Diet screen (macro ring, day strip), Add food sheet + service | DONE | AH-052, AH-017 |

### EPIC 6 — Feed · [epic-6-feed.md](epic-6-feed.md)
| ID | Story | Status | Depends on |
|----|-------|--------|-----------|
| AH-060 | Schema: posts, post_likes, post_comments | DONE | AH-010 |
| AH-061 | Auto-create posts from workout/cardio/eval + manual posts | DONE | AH-060, AH-033 |
| AH-062 | Feed timeline (fan-out-on-read) + filters + hydration | DONE | AH-061, AH-021 |
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

**Progress:** 36 done / 47. **Epics 1–5 fully closed; Epic 6 (Feed) 3/5 — schema + auto-post + feed timeline + profile feed.**

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
  - **AH-043 DONE** — Flutter Evolve / New Evaluation / Graph
    detail. **Closes Epic 4.** Five models mirror the backend DTOs;
    one service; three screens; main_shell wired.
    Files added
      * `models/responses/{evaluation_measurement,
        evaluation, evaluation_summary, metric_series}` (5 classes
        in 4 files — `MetricPoint` + `MetricSeries` share a file).
      * `services/api/evaluation_api_service.dart` — `listRecent`,
        `getById`, `create`, `getSeries`. All through
        `HttpInterceptor` so 401 → silent refresh + retry.
      * `screens/evolution_screen.dart` — three sections loaded in
        parallel via two awaited futures:
          - **Hero stats** card with latest weight + body-fat (when
            present) + date.
          - **Weight chart** (4w default) — `fl_chart` mini-line,
            tappable, opens `GraphDetailScreen('weight', '12w')`.
          - **Quick chips** for body-fat, waist, right arm — each
            opens the graph detail with the matching point id.
          - **Recent evaluations list** (top 10) — slim summary
            rows.
        FAB → `NewEvaluationScreen`; pull-to-refresh re-fetches
        both calls; loading / error / empty states.
      * `screens/new_evaluation_screen.dart` — bf-method-aware
        form. Weight required; body-fat method dropdown gates
        downstream inputs:
          - **None** → weight-only check-in.
          - **Manual** → body-fat % field appears.
          - **Jackson-Pollock 7-site** → 7 skinfold inputs (mm)
            for chest, abdomen, thigh, tricep, subscapular,
            suprailiac, midaxillary.
          - **Navy** → neck + waist (cm); hip added when cached
            user's `sex == 'female'`.
        **Profile-gating UX** — when the chosen method needs
        `users.sex` / `users.age` / `users.heightCm` and the
        cached `UserResponse` doesn't have them, the section
        shows an inline red banner ("Set your sex in profile
        settings first") and `_save` short-circuits with a
        friendly error. Server still validates as a defense-in-
        depth.
        **"Other measurements" section** — bottom-sheet picker
        with segmented circumference/skinfold and a chip grid of
        common points + a "Custom point id" text field; honors
        the schema's free-form `point_id` design. Collection skips
        point ids already supplied by the method section so the
        backend's duplicate-point validation doesn't bite the
        submit.
      * `screens/graph_detail_screen.dart` — full metric chart
        with range picker (4w / 12w / 6m / 1y segmented button).
        Chart is `fl_chart` line with curved interpolation, area
        fill, and dot markers; below it a list of every point
        newest-first so users can scan exact values. Loading /
        error / empty states; ranges reload on change.
    Files modified
      * `main_shell.dart` — Evolve tab now hosts `EvolutionScreen`
        (replaces placeholder).
    Verification
      * `flutter analyze` clean (one curly-braces lint fixed
        first time around).
      * `flutter test` 2/2 green.
      * No backend changes — 167/167 still green.
    Design choices captured
      * **Profile-gating before submit** — proactive UX. The
        backend's `BF_MISSING_USER_FIELD` is the source of truth;
        the client just gets there sooner with a friendlier
        message.
      * **Chart x-axis = days since first sample** rather than raw
        epoch ms because `fl_chart` complains about very large
        x-values. The visual order is correct; explicit time-axis
        labels are out of scope (the date list under the chart
        carries that information).
      * **Mini-line on Evolve, full line on detail** keeps the
        Evolve tab fast — only one extra series fetch (weight 4w)
        beyond the recent list.
      * **Free-form `point_id` honored end-to-end** — picker
        offers common points but the custom-field always wins
        when filled, matching the schema design.
  - **AH-050 DONE** — Nutrition foundation. Flyway
    `V20260529150000__create_nutrition_tables.sql` creates the 6
    tables per `02-data-model.md §4.6` with the MVP simplifications
    spelled out in the migration header: no `client_uuid` (online-
    first), no `assigned_by_coach_id` FK on diet_plans yet (Epic 7's
    `assignments` table hasn't landed — the
    `diary_entries.source = 'coach'` enum value is already accepted
    so the data shape stays forward-compatible), no `tsvector`
    full-text search (LOWER(name) index is enough for AH-051's
    substring search, same call as exercises).
    Tables
      * **`foods`** — catalog. `is_global = TRUE` rows are the seed
        list (`created_by IS NULL`); user customs belong to one
        user (`created_by NOT NULL`). XOR rule mirrors exercises
        (AH-030). Macros are `NUMERIC(7,2)`; `serving_size_g > 0`;
        all macros ≥ 0; optional `fiber_g`, `sodium_mg`.
      * **`diet_plans`** — reusable plans owned by a user.
        `is_library = TRUE` flags coach library entries.
      * **`diet_meals`** — named meal inside a plan ("Breakfast",
        "Post-workout"). `time_hint` is free-form HH:MM TEXT; the
        UI uses it to sort the day's meals but the server doesn't
        parse it.
      * **`meal_items`** — one food entry inside a meal with target
        amount + unit + position. `unit ∈ {g, ml, portion}`.
      * **`diary_entries`** — what the user actually ate, when.
        `meal_label` is free-form so users can bucket entries
        however they like ("Pre-workout", "Cheat meal") without
        being forced into a fixed enum. `source ∈ {self, plan,
        favorite, coach}`.
      * **`favorites`** — per-user food bookmarks for quick-add.
        UNIQUE(user_id, food_id) — favouriting twice is a no-op
        at the data layer.
    Sharp edges encoded:
      * `UNIQUE(plan_id, position)` on diet_meals;
        `UNIQUE(meal_id, position)` on meal_items so reordering
        is an UPDATE not an append.
      * `UNIQUE(user_id, food_id)` on favorites.
    Cascade choices:
      * **delete plan** → meals → items (CASCADE chain).
      * **delete user** → CASCADE to diet_plans (then meals + items
        in the chain), diary_entries, favorites, and their custom
        foods (`foods.created_by → CASCADE`).
      * **delete food** → RESTRICTed when referenced by meal_items
        or diary_entries (catalog can't drop out from under
        history); but CASCADE on favorites (a favorite is just a
        bookmark — losing the food makes the bookmark meaningless).
        The user-delete chain still works because the meal_items
        and diary_entries are gone before `foods.created_by`
        CASCADE fires.
    Indexes documented in the spec are all present:
    `idx_foods_name_lower` for case-insensitive search;
    `idx_foods_created_by` partial for "my customs";
    `idx_diet_plans_owner (owner_id, id DESC)`;
    `idx_diet_meals_plan (plan_id, position)`;
    `idx_meal_items_meal (meal_id, position)`;
    `idx_diary_entries_user_eaten (user_id, eaten_at DESC)` —
    "today's diary" + "week summary" both scan that way;
    `idx_favorites_user (user_id, id DESC)`.
    `NutritionSchemaIT` 13/13 (150 ms): tables + indexes present,
    foods XOR enforced (both legal shapes succeed), macros must be
    non-negative, serving size must be positive, meal_items unit
    rejects unknown ('oz') and accepts 'g', diary_entries source
    rejects unknown ('import') and accepts 'coach' (forward-
    compatible), diet_meals position unique per plan + meal_items
    position unique per meal + favorites unique per (user, food),
    plan → meals → items cascade, food deletion RESTRICTed by
    meal_items + diary_entries (two separate cases), favorites
    CASCADE on food deletion, user deletion cascades to plans +
    diary + favorites.
    Full backend suite: **180/180** (20 ITs + 3 unit).
  - **AH-051 DONE** — food DB. Mirrors AH-031 (exercise catalog)
    almost exactly. Seed migration
    `V20260529160000__seed_global_foods.sql` loads ~27 staples
    (proteins, dairy, grains, fruit, vegetables, fats) with macros
    per 100 g taken from USDA averages, rounded to 1 decimal.
    `brand` stays NULL — these are generic foods; brand-name
    products belong in user customs.
    Two endpoints
      * **`GET /api/foods?q=&cursor=&limit=`** — visibility-
        filtered search: global rows + caller's own customs.
        Case-insensitive substring on name. Cursor on id ASC
        (globals seeded first → low ids → catalog first then
        user's additions). Empty-after-trim `q` treated as no
        filter.
      * **`POST /api/foods`** — create a custom (`is_global = false`,
        `created_by = caller`). Bean validation mirrors the schema
        CHECKs (`servingSizeG > 0`, macros ≥ 0, name non-blank).
        Reject duplicate names against the caller's own customs
        (409 `FOOD_ALREADY_EXISTS`) — globals are intentionally
        forkable so a user can record their own batch.
    **Same null-q query-split trick** as the exercise catalog: two
    JPQL methods (`searchVisible` and `searchVisibleByName`)
    because passing null into `CONCAT('%', :q, '%')` trips
    PostgreSQL's type inference into `lower(bytea)` (doesn't exist).
    Files: `model/Food`, `repository/FoodRepository`,
    `dto/{FoodDto, CreateFoodRequest}`, `service/FoodService`,
    `controller/FoodController`, +`FOOD_ALREADY_EXISTS` MessageCode.
    `FoodCatalogIT` 12/12 (3.5 s): seed loaded (≥ 25 globals),
    search without token → 401, case-insensitive substring,
    blank/whitespace query returns full catalog, cursor pagination
    walks pages without overlap, create marks custom + only owner
    sees it, duplicate name from same user → 409, same name OK
    across users, naming a custom after a global is allowed,
    validation rejects negative macros / zero serving size /
    blank name.
    **Sharp edge** caught the first time around: my IT helper
    URL-encoded the `q` parameter with literal `%20` substitution.
    Spring didn't decode them as expected and the search ran with
    `q = "%20%20%20"`. Switched to `RestTemplate`'s URI template
    variable substitution (`{q}` placeholder + vars map), same as
    `CardioActivityIT`/`EvaluationListAndSeriesIT`. That's the
    pattern to use for any future query-string IT helper.
    Full backend suite: **192/192** (21 ITs + 3 unit).
  - **AH-052 DONE** — active diet plan + day endpoint. Flyway
    `V20260529170000__add_user_active_diet_plan.sql` adds
    `users.active_diet_plan_id BIGINT REFERENCES diet_plans(id)
    ON DELETE SET NULL`. Nullable on purpose — users without an
    active plan still get meaningful day totals (just no target /
    remaining numbers); SET NULL on plan deletion so a plan can be
    removed without blocking on the user pointer.
    Three endpoints
      * **`GET /api/diet/active`** — hydrated active plan
        (meals → items → food + per-item scaled macros + plan
        `dailyTarget`) or null when no plan is set.
      * **`POST /api/diet/active`** — body `{planId}`. Validates
        plan ownership (404 `DIET_PLAN_NOT_FOUND` for unknown /
        another-user's plan); null `planId` clears the active
        pointer. Returns the hydrated plan or null.
      * **`GET /api/diet/day?date=YYYY-MM-DD`** — payload
        `{date, entries, totals, target, remaining}`. Date is
        optional; defaults to today via the `Clock` bean. Entries
        oldest-first; totals + target + remaining all use the
        shared `Macros` record so they're comparable element-wise.
        Remaining can go negative when over target (the chart will
        render "1200 kcal over").
    **Macro scaling rule** (encoded in `DietService.scaleMacros`):
      * `g` / `ml` → `macro = amount × food.macro / food.serving_size_g`.
        Treats ml as g for now — most macro-relevant liquids
        (milk, juice, broth) are ~1 g/ml.
      * `portion` → `macro = amount × food.macro` (one portion =
        one × serving_size_g).
    All scale-2 HALF_UP on the way out so JSON ↔ DB ↔ in-memory
    stays round-trip stable with `NUMERIC(7,2)`. Null macros
    (fiber/sodium not stored on a food) propagate as null through
    addition so the wire payload renders dashes rather than
    misleading zeros — except the day endpoint's totals, which
    treat absent values as zero for the additive case (you can't
    eat null fiber).
    **Sharp edge encoded:** the body-fat XOR rule has a cousin
    here — `target` and `remaining` are both null or both set, on
    a "do you have an active plan?" axis. Same pattern as
    `TodayPlanResponse` from AH-032: four nullable combinations
    are all real states.
    **Hydration strategy:** three batched queries (meals,
    items-IN, foods-IN) instead of N+1 fetches. Plan hydration +
    target computation share the same pattern; same as training
    session DTO assembly.
    **GlobalExceptionHandler** got a new handler:
    `MethodArgumentTypeMismatchException` → 400 `VALIDATION_FAILED`
    (Spring's `@DateTimeFormat` binding failure on `?date=not-a-date`
    was falling through to the catch-all 500). Applies broadly to
    any query-param type-binding failure.
    **Plan creation deferred** — same pattern as workout-template
    CRUD. The IT seeds plans via `JdbcTemplate`. Athlete-side plan
    creation lands later or with Epic 7 (coach assignments).
    Files added
      * `model/{DietPlan, DietMeal, MealItem, DiaryEntry}`
      * `repository/{DietPlan, DietMeal, MealItem, DiaryEntry}Repository`
      * `dto/{DietPlanDto, DietMealDto, MealItemDto, DiaryEntryDto,
        Macros, DayResponse, SetActivePlanRequest}`
      * `service/DietService`
      * `controller/DietController`
      * `enums/MessageCode` +`DIET_PLAN_NOT_FOUND`
      * `exception/GlobalExceptionHandler` +
        `MethodArgumentTypeMismatchException` handler
      * `db/migration/V20260529170000__add_user_active_diet_plan.sql`
    Files modified
      * `model/User` — `activeDietPlanId` field (no UserDto exposure)
    `DietActiveAndDayIT` 16/16 (6 s) using the same fixed-clock
    `@TestConfiguration` (Wed 2026-05-27):
      * active — null when no plan set; set then GET returns
        hydrated plan with per-item macros and `dailyTarget` (200g
        chicken + 150g rice → 525 kcal, 66.05 g protein); clear by
        sending null planId; reject another user's plan → 404
        DIET_PLAN_NOT_FOUND; reject unknown plan → 404; deleting
        the active plan nulls the user pointer (ON DELETE SET
        NULL); no token → 401 on GET + POST.
      * day — empty totals + null target/remaining when no diary
        + no plan; sums diary entries scaled by unit (200g + 1
        portion of chicken → 495 kcal); target from active plan;
        remaining = target − totals; remaining goes negative when
        over; window boundaries respected (yesterday-23:59 and
        tomorrow-00:01 excluded); date param defaults to today;
        no leakage between users; no token → 401; bad date format
        → 400 VALIDATION_FAILED (via new handler).
    Full backend suite: **208/208** (22 ITs + 3 unit).
  - **AH-053 DONE** — diary CRUD + favorites. **Closes the backend
    half of Epic 5.** Five new endpoints, all on the existing
    `DietController`:
      * **`POST /api/diet/diary`** — `{foodId, amount, unit,
        mealLabel?, eatenAt?, source?}`. Validates food visibility
        (global or owned by caller) via the new
        `FoodRepository.findByIdAndVisibleTo` — referencing another
        user's custom returns 404 `FOOD_NOT_FOUND` (no
        existence-vs-permission timing side channel). `unit ∈
        {g, ml, portion}`, `amount > 0`, `source ∈
        {self, plan, favorite}` — `coach` is rejected from the
        client API (reserved for Epic 7's assignment path).
        `eatenAt` defaults server-side to now() when null; the
        response echoes the same `DiaryEntryDto` shape the day
        endpoint uses, with macros already scaled.
      * **`DELETE /api/diet/diary/{id:\\d+}`** — 204. Validates
        owner; 404 `DIARY_ENTRY_NOT_FOUND` on someone else's row.
      * **`GET /api/diet/favorites?cursor=&limit=`** — newest-first
        cursor pagination on id DESC. Each item carries the
        hydrated `FoodDto` so the Quick-Add list renders macros
        without a follow-up call.
      * **`POST /api/diet/favorites`** — `{foodId}`. Find-or-insert:
        favoriting the same food twice returns the existing row
        (idempotent contract; doesn't surface the schema's
        `UNIQUE(user_id, food_id)` as a 409). Validates food
        visibility same as diary.
      * **`DELETE /api/diet/favorites/{foodId:\\d+}`** — 204.
        Deletes by natural key, idempotent — second call is still
        204 (the API contract is "favourite is gone", not "I
        removed a row").
    **Sharp edge encoded** — `FoodRepository.findByIdAndVisibleTo`
    is the single chokepoint for "is the caller allowed to
    reference this food?" Both the diary and favorites paths use
    it so visibility logic doesn't fork.
    Files added
      * `model/Favorite`
      * `repository/FavoriteRepository`
      * `dto/{CreateDiaryEntryRequest, AddFavoriteRequest, FavoriteDto}`
      * `enums/MessageCode` + `FOOD_NOT_FOUND`,
        `DIARY_ENTRY_NOT_FOUND`
      * test: `DiaryAndFavoritesIT` (21 cases)
    Files modified
      * `repository/FoodRepository` + `findByIdAndVisibleTo`
      * `service/DietService` + `addDiaryEntry`, `deleteDiaryEntry`,
        `listFavorites`, `addFavorite`, `removeFavorite`,
        `trimToNull` helper, `FavoriteRepository` dep
      * `controller/DietController` — 5 new endpoints + clampLimit
    `DiaryAndFavoritesIT` 21/21 (6 s):
      * **Diary create** — happy path returns 201 with scaled
        macros (200g chicken → 330 kcal, 62 g protein), defaults
        source=self + eatenAt=now, accepts plan + favorite
        sources, rejects coach source from client (400), rejects
        unknown food / another user's custom (404
        FOOD_NOT_FOUND), rejects bad unit / zero amount (400),
        no token → 401.
      * **Diary delete** — 204 + row gone, another user's entry →
        404 DIARY_ENTRY_NOT_FOUND, unknown → 404.
      * **Favorites** — list empty by default, add returns 201
        with hydrated food, add is idempotent (same row id on
        dup; single DB row), add rejects another user's custom →
        404, list newest-first per user with no leakage, delete
        by foodId is 204 and idempotent (second call still 204),
        delete only touches caller's row (two users favoriting
        the same food don't affect each other), food deletion
        cascades to favorites (schema CASCADE), all favorite
        endpoints → 401 without token.
    Full backend suite: **229/229** (23 ITs + 3 unit).
  - **AH-054 DONE** — Flutter Diet screen + Add-Food sheet.
    **Closes Epic 5.** Eight models mirror the backend DTOs; one
    service; one custom-painted widget; two screens; main_shell
    wired.
    Files added
      * `models/responses/{macros, food, meal_item, diet_meal,
        diet_plan, diary_entry, favorite, day_response}.dart` —
        manual `fromJson` per CONVENTIONS.
      * `services/api/diet_api_service.dart` — `day(date?)`,
        `getActivePlan()` (handles empty body → null), `addDiaryEntry`,
        `deleteDiaryEntry`, `listFavorites`, `addFavorite`,
        `removeFavorite`, `searchFoods`. All routed through
        `HttpInterceptor` so 401 → silent refresh + retry.
      * `widgets/macro_ring.dart` — custom-painted three-concentric-
        arc ring (protein / carb / fat). Each arc fills
        `consumed / target` clamped to [0, 1]; when target is null
        the ring shows the colours at full sweep and the legend
        below reads raw grams. Kcal big in the centre, "of X kcal"
        subtitle when target is set.
      * `screens/add_food_sheet.dart` — modal bottom-sheet with two
        tabs (Search / Favorites). Search is 250 ms-debounced
        across the global + custom catalog; Favorites lists the
        caller's quick-add bookmarks. Per-row star toggles the
        favorite (idempotent backend, no need to track local
        state). Tapping a food reveals an amount/unit form with:
        - amount pre-filled to `food.servingSizeG` so 1-tap logs
          a 100 g default,
        - a live "Adds: N kcal · P/C/F" preview that mirrors the
          backend's macro scaling rule (`g`/`ml` divide by serving
          size, `portion` multiplies directly),
        - a "Save as favorite for Quick-Add" checkbox so the user
          can favourite + log in one tap (best-effort favorite —
          a partial failure doesn't surface as primary error),
        - optional meal-label field (pre-filled when launched from
          a meal section's "Add" button).
      * `screens/diet_screen.dart` — Diet tab.
        - Day navigator with prev / next arrows and tappable date
          (opens a date picker, range [2020, now + 1y]).
        - `MacroRing` centered, with a legend row beneath showing
          consumed and target grams per macro.
        - Empty-state banner under the ring: "No active diet plan
          — showing raw totals. Plan support arrives with coaching."
          (Per Option A — plan creation deferred to Epic 7 /
          AH-054b.)
        - Diary entries grouped by `mealLabel` (defaulting to
          "Other" when null), preserving insertion order for
          stable rendering. Each meal section has its own "Add"
          button that pre-fills the meal label.
        - Swipe-to-delete on entries with a confirm dialog (avoids
          accidental loss of a 15-meal-day-of-tracking entry).
        - FAB → AddFoodSheet without a default meal label.
        - Pull-to-refresh re-fetches the day payload.
    Files modified
      * `main_shell.dart` — Diet tab now hosts `DietScreen`
        (replaces the placeholder).
    Verification
      * `flutter analyze` clean.
      * `flutter test` 2/2 green.
      * No backend changes — 229/229 still green.
    Design choices captured
      * **Macro ring degrades cleanly when target is null.** No
        plan → full-sweep coloured arcs + legend reads "Xg" not
        "X / Y g"; the design doesn't need a separate "no plan"
        widget.
      * **Amount pre-fill to serving size** makes 1-tap logging
        the common case. Easy override (the field is editable
        immediately), but a chicken-breast-100g-default is the
        most-likely amount.
      * **Live macro preview in the amount form** mirrors the
        backend's exact scaling rule so the user sees the same
        numbers they'll get after submit. Less likely to feel
        like the backend "lied" on rollups.
      * **Best-effort favorite on submit** — if the user checks
        "save as favorite" and the favorite POST fails, the
        diary entry still landed and we don't surface a partial
        error. The next AddFoodSheet open will refresh favorites
        anyway.
      * **Meal-label sections pre-fill the Add button** so the
        common "Breakfast → add eggs → add toast" flow is two
        taps less.
  - **AH-060 DONE** — Feed foundation. Flyway migration
    `V20260529180000__create_feed_tables.sql` creates the three
    feed tables per `02-data-model.md §4.3` with the MVP
    simplifications spelled out in the migration header:
      * no `client_uuid` (online-first; no offline reconciliation)
      * no `image_media_id` FK yet — the `media_assets` table
        arrives with Epic 9. The column exists so AH-061 can land
        posts that reference a media row when Epic 9 ships, but
        the FK constraint comes with that migration.
      * no `feed_entries` materialized timeline — the architecture
        spec flags this as a Phase 2 fan-out-on-write optimization;
        MVP uses fan-out-on-read (AH-062 will scan `posts` directly
        with the partial active-feed index).
    Tables
      * **`posts`** — one row per published item. `type ∈ {workout,
        run, cycle, evolution, manual}` discriminates how the card
        renders. `source_ref_type` + `source_ref_id` are a soft
        link back to the row that triggered the auto-post (no FK
        because we don't want a workout-session delete to be
        blocked by a post; the soft link goes stale gracefully).
        `payload` is a JSONB snapshot of what the card rendered at
        publish time — coach renames an exercise tomorrow doesn't
        rewrite yesterday's feed cards. `like_count` +
        `comment_count` denormalized so a feed card render is O(1).
        `visibility ∈ {public, followers, private}` defaults to
        `followers`. Soft-delete via `deleted_at` so threads stay
        consistent.
      * **`post_likes`** — composite PK on (post_id, user_id)
        enforces "one like per (post, user)" — a second tap is a
        no-op at the data layer. CASCADE on both FKs.
      * **`post_comments`** — soft-delete via `deleted_at` (collapse
        a comment without orphaning the thread); body kept in place
        for moderation audit. Hard delete only on GDPR.
    Sharp edges encoded as CHECKs:
      * **source_ref XOR** — `source_ref_type` and `source_ref_id`
        are both null (manual post) or both set (auto-post).
        Same pattern as body-fat ↔ method pairing (AH-040).
      * `type` ∈ {workout, run, cycle, evolution, manual};
        `visibility` ∈ {public, followers, private};
        `source_ref_type` ∈ {workout_session, cardio_activity,
        evaluation} when set.
      * `like_count ≥ 0`, `comment_count ≥ 0` so the denormalized
        counters can't go pathological.
      * `LENGTH(body) > 0` on comments — no whitespace-only
        comments slipping through.
    Cascade choices:
      * **delete post** → CASCADE to `post_likes` + `post_comments`.
      * **delete user** → CASCADE to their authored posts (then
        the post → likes / comments chain), and their own like /
        comment rows directly.
    Indexes documented in the spec are all present:
      * `idx_posts_author_created (author_id, created_at DESC)` —
        profile timeline reads.
      * `idx_posts_feed_created_active (created_at DESC) WHERE
        deleted_at IS NULL` — **partial** index for the hottest
        query (home feed read), so soft-deleted posts don't bloat
        the scan.
      * `idx_post_likes_user (user_id, created_at DESC)` —
        "what did I like recently?" / liker hydration.
      * `idx_post_comments_post_created (post_id, created_at)` —
        chronological thread loads.
    `FeedSchemaIT` 11/11 (95 ms): tables + indexes present, type
    CHECK rejects unknown + accepts all 5 valid values, visibility
    CHECK rejects unknown + accepts 'public', source_ref XOR fires
    in both directions and both legal shapes (manual = both null;
    auto = both set) succeed, source_ref_type rejects unknown,
    counters non-negative + default to zero, post_likes PK blocks
    duplicate likes, post_comments body must be non-empty, delete
    post → likes + comments cascade, delete user → their posts +
    their likes + their comments cascade (other users' posts
    survive).
    Full backend suite: **240/240** (24 ITs + 3 unit).
  - **AH-061 DONE** — auto-create posts + manual posts.
    New `PostService` exposes four publish entry points: three
    internal hooks (`publishFromWorkout`, `publishFromCardio`,
    `publishFromEvaluation`) called from the originating service +
    one public manual publish (`publishManual`). Each call: builds
    the JSONB `payload` snapshot, sets the soft link
    (`source_ref_type` + `source_ref_id`) per the data-model spec,
    stamps the `type` enum value, saves the row, bumps
    `user_counters.posts` (+1 via new `adjustPosts`).
    JSONB mapping uses Hibernate's `@JdbcTypeCode(SqlTypes.JSON)`
    on a `Map<String, Object>` field — Hibernate handles Jackson
    serialization on read/write, no custom converter needed.
    Hook-point integration in the existing services
      * **`TrainingService.finishSession`** — after the rollups +
        PR pass + counter increment, calls
        `postService.publishFromWorkout(session)`. Snapshot fields:
        `title, totalVolumeKg, totalSets, prCount, durationSeconds`.
      * **`CardioService.create`** — after the row save, calls
        `postService.publishFromCardio(activity)`. Snapshot fields:
        `type, distanceM, durationSeconds, avgPaceSPerKm?,
        avgHr?, kcal?`. Cardio type → post type:
        `cycle → cycle`, `walk → run`, `run → run` (the design's
        post-type enum doesn't distinguish walk; walks render the
        same card as runs).
      * **`EvaluationService.create`** — after the row + measurements
        save, calls `postService.publishFromEvaluation(saved)`.
        Snapshot fields: `weightKg, bodyFatPct?, bfMethod?,
        evaluatedAt`.
    **Sharp edge encoded** — every hook call is wrapped in
    try/catch (`RuntimeException` → log + continue). A snapshot
    failure can't roll back the originating transaction; a workout
    that finished should stay finished even if the feed card
    couldn't be persisted. The user can manually re-post if needed.
    Endpoints
      * **`POST /api/posts`** — body `{title?, note?, visibility?}`
        for manual posts. Type is always `manual`. Visibility
        defaults to `followers`; bean validation enforces the
        whitelist (`public | followers | private`).
      * **`DELETE /api/posts/{id:\\d+}`** — soft-delete: stamps
        `deleted_at = now()` on the row + decrements counter by 1.
        Author-scoped — 404 `POST_NOT_FOUND` on someone else's or
        already-deleted (no disclosure between "doesn't exist"
        and "not yours" by timing).
    Files added
      * `model/Post` (JSONB payload via `@JdbcTypeCode`)
      * `repository/PostRepository`
      * `dto/{PostDto, CreateManualPostRequest}`
      * `service/PostService`
      * `controller/PostController`
      * `enums/MessageCode` + `POST_NOT_FOUND`
      * test: `PostsIT` (14 cases)
    Files modified
      * `repository/UserCountersRepository` + `adjustPosts`
      * `service/TrainingService` + `PostService` dep + try/catch
        publish call in `finishSession`
      * `service/CardioService` + `PostService` dep + try/catch
        publish call in `create`
      * `service/EvaluationService` + `PostService` dep + try/catch
        publish call in `create`
    `PostsIT` 14/14 (4 s): manual post returns 201 with defaults
    (type=manual, visibility=followers, source-ref nulled,
    counters zeroed); visibility override accepts all three; bad
    visibility → 400 VALIDATION_FAILED; no token → 401; counter
    increments on each manual post; soft-delete stamps deleted_at
    + decrements counter; delete twice → 404 POST_NOT_FOUND;
    delete another user's post → 404 + post untouched; delete
    unknown → 404; finish workout → workout post with
    source_ref_type=workout_session + correct ref id + counter
    bumped; create run cardio → run post; create cycle cardio →
    cycle post; create walk cardio → run post (walk maps to run);
    create evaluation → evolution post with
    source_ref_type=evaluation + correct ref id.
    Full backend suite: **254/254** (25 ITs + 3 unit).
  - **AH-062 DONE** — feed reads (home + profile) with hydration.
    Two endpoints, both on the new `FeedController`:
      * **`GET /api/feed?cursor=&limit=&type=...`** — home
        timeline. Fan-out-on-read: viewer's own posts (any
        visibility, including private) + their followees' posts
        where visibility is not `private`. **Non-followed users'
        public posts intentionally excluded** — that's a future
        "explore" feed; the home timeline is purely follow-graph.
      * **`GET /api/users/{handle}/posts?cursor=&limit=`** —
        profile feed. Visibility derived from the viewer-author
        relationship: self → all three; follower → public +
        followers; stranger → public only.
    Visibility logic centralized:
    `FeedService.allowedVisibilitiesFor(viewerId, authorId)` is
    the single chokepoint for profile-feed access. Home feed uses
    the equivalent inline rule in the JPQL: `authorId = viewerId
    OR visibility <> 'private'`, combined with the follows
    subquery filter on the author.
    Type filter accepts a comma-separated list
    (`?type=workout,run`). Unknown values are silently dropped
    (filtered set empty → unfiltered branch) rather than failing
    the request — common-case forgiving.
    **Hydration strategy:** two batched queries per page
    regardless of page size — `userRepository.findAllById(authorIds)`
    for the author DTOs + `postLikeRepository.findLikedPostIds(viewerId,
    postIds)` for the viewer-scoped `iLiked` flag. The wire shape
    is `{post, author, iLiked}` per item.
    Cursor pagination on `id DESC` (surrogate id is monotonic and
    roughly time-ordered; matches the `created_at DESC` the feed
    wants in practice). Same `limit + 1` trick as elsewhere.
    Files added
      * `model/{PostLike, PostLikeKey}` — composite-PK entity for
        the like row. AH-063 will add the write paths; AH-062
        only needs the batched read for `iLiked`.
      * `repository/PostLikeRepository` + `findLikedPostIds(viewerId,
        Collection<Long>)`
      * `dto/FeedItemDto` — `{post, author, iLiked}` wrapper
      * `service/FeedService` + home / profile flows +
        `allowedVisibilitiesFor` + `parseTypes` (csv → filtered
        list, drops unknowns)
      * `controller/FeedController`
      * test: `FeedTimelineIT` (17 cases)
    Files modified
      * `repository/PostRepository` + `findHomeFeed`,
        `findHomeFeedByTypes`, `findProfileFeed` — three JPQL
        queries that hit the partial
        `idx_posts_feed_created_active` index from AH-060.
    `FeedTimelineIT` 17/17 (18 s):
      * **home feed visibility matrix** — empty when no posts;
        own posts all visibilities visible; followee public +
        followers visible; followee private excluded; non-followed
        users' posts excluded (even public).
      * **home feed mechanics** — soft-deleted posts excluded;
        newest-first cursor pagination walks pages; type filter
        narrows to single + comma-separated lists; unknown type
        silently drops to unfiltered.
      * **hydration** — author surfaces with handle, iLiked is
        true when viewer liked, false when only someone else did.
      * **profile feed visibility matrix** — self sees all three
        visibilities, follower sees public + followers, stranger
        sees public only.
      * **profile feed mechanics** — soft-deleted excluded, unknown
        handle → 404, no token → 401 (both endpoints), cursor
        pagination walks pages.
    Full backend suite: **271/271** (26 ITs + 3 unit).
  - **Next:** **AH-063 — like / comment / share**:
    `POST /api/posts/{id}/likes` + `DELETE /api/posts/{id}/likes`
    (idempotent — second tap is a no-op like favorites);
    `POST /api/posts/{id}/comments` + `DELETE
    /api/comments/{id}` (author-scoped soft-delete);
    `GET /api/posts/{id}/comments?cursor=` (chronological thread);
    counter maintenance on `posts.like_count` + `comment_count`.
    Share is a client-side concern (`Share.share()` in Flutter,
    not a backend endpoint) so AH-063 backend lands only the
    first four.
