# CONVENTIONS — AthleteHub (MVP)

> **This document is authoritative for how we build AthleteHub.** Where it
> conflicts with the older architecture docs (which described a modular monolith
> / hexagonal backend and a Riverpod, offline-first, feature-first Flutter app),
> **this document wins.** AthleteHub MVP deliberately follows the **same layered
> structure and conventions as the `lotuga` project** (`C:\Users\Fernando\Documents\git\lotuga`).
>
> Rationale: for an MVP we want the simplest structure that ships. Matching an
> existing, working team codebase beats imposing a textbook architecture — it
> keeps both repos consistent and lets us reuse patterns we already trust.

## 1. What changed vs. the original architecture plan

| Topic | Original plan (superseded) | MVP decision (this doc) |
|---|---|---|
| Backend structure | Modular monolith, bounded contexts, hexagonal layers, per-context schemas | **Plain layered**: `controller → service → repository` + `model`/`dto`, one package |
| Backend events | Domain events + transactional outbox + Kafka | **None for MVP**. Direct service calls. Scheduled jobs via `@Scheduled` (`scheduler/`) |
| Flutter structure | Feature-first clean architecture, Riverpod, offline-first Drift DB, sync outbox | **Type-based folders** (`screens`/`services`/`models`/`widgets`), **plain `setState`**, `http` package, no local DB |
| API | REST + later GraphQL/BFF | **REST only** (`http` package client). No GraphQL |
| Caching/Redis | Redis cache + timeline fan-out | **None for MVP** (rate limiting in-process via bucket4j) |
| Realtime | WebSocket/STOMP | **Push only** (Firebase) for MVP; polling for feed/chat. Defer WS |
| Repo | Implied separate services | **Mono repo**: `api/` + `client/` |

Everything still true from the original docs: PostgreSQL is the system of record,
the **relational schema** in `02-data-model.md` is still the basis (simplified —
drop `client_uuid`/offline columns and per-context schemas for MVP), Flyway for
migrations, JWT auth with refresh tokens, Apple/Google social login, Firebase push.

## 2. Mono-repo layout

```
athletehub/                      # repo root (git init here)
├── api/                         # Spring Boot backend (Maven)
├── client/                      # Flutter app
├── docs/                        # architecture docs + stories  (recommend flattening AthleteHub/docs → docs)
│   ├── architecture/
│   └── stories/
├── deploy/                      # deploy configs (later)
├── README.md
└── .gitignore
```

> Cleanup note: the plan currently lives at `athletehub/AthleteHub/docs/...`.
> Recommended one-time move: `AthleteHub/docs` → `docs` at the repo root, then
> delete the redundant `AthleteHub/` folder. Tracked in story **AH-001**.

## 3. Backend conventions (`api/`) — mirror of lotuga

**Stack:** Java 25, Spring Boot 4.0.0, Maven, PostgreSQL, Flyway, Lombok, JJWT,
Spring Security + OAuth2 client, Spring Mail, Actuator + Micrometer/Prometheus,
bucket4j (rate limiting), Firebase Admin SDK (push), Apache Commons.

**Base package:** `com.example.athletehub` (lotuga uses `com.example.lotuga`).

**Package layout — organize by technical layer, NOT by feature:**

```
com.example.athletehub
├── config/        # @Configuration: Security wiring lives in security/, here: Web, Jackson, Scheduling, Firebase, Database
├── controller/    # @RestController — thin; validate input, call a service, return a DTO
├── dto/           # request/response records & ApiResponse<T> wrapper; *Request / *Response / *Dto
├── enums/         # shared enums (Role, BookingStatus-style)
├── exception/     # custom exceptions + @RestControllerAdvice global handler
├── model/         # @Entity JPA classes (one per table)
├── repository/    # Spring Data JPA interfaces (extends JpaRepository)
├── scheduler/     # @Scheduled jobs (e.g. eval reminders) — guarded for single-instance
├── security/      # JwtUtil, JwtAuthenticationFilter, SecurityConfig, UserPrincipal, CustomUserDetailsService, RateLimitingFilter
├── service/       # @Service — all business logic; transactions (@Transactional) live here
└── util/          # helpers
```

**Rules**
- **Layering:** `controller → service → repository`. Controllers never touch
  repositories or entities directly; services own transactions and return DTOs.
- **DTOs at the edge:** never serialize JPA entities to JSON. Map entity→DTO in
  the service. Use an `ApiResponse<T>` envelope (mirror `dto/ApiResponse.java`).
- **Lombok** for entities/DTOs (`@Getter/@Setter/@Builder/@RequiredArgsConstructor`).
- **Migrations:** Flyway, `src/main/resources/db/migration/V<yyyyMMddHHmmss>__description.sql`
  (timestamp prefix exactly like lotuga). Never edit a shipped migration; add a new one.
- **Auth:** JWT access token + `RefreshToken` entity/repository/service; social
  login via OAuth2 success/failure handlers. Copy lotuga's `security/` shape.
- **Validation:** Bean Validation annotations on request DTOs; handled by the
  global exception advice → consistent error JSON.
- **Config:** `.env` via `spring-dotenv`; `application.yml` profiles (`dev`, `test`, `prod`).
- **No** outbox, Kafka, Redis, or bounded-context schemas in MVP.

**Testing (mirror lotuga):**
- Unit/slice tests `*Test` (Surefire), H2 for fast slices.
- Integration tests `*IT` (Failsafe) on **Testcontainers PostgreSQL** so Flyway
  runs as in prod; **GreenMail** for email flows; **WireMock** for external APIs;
  **Awaitility** for async. Spring Security test for auth.

## 4. Frontend conventions (`client/`) — mirror of lotuga

**Stack:** Flutter (Dart SDK ^3.10), `http`, `flutter_secure_storage`,
`flutter_dotenv`, `flutter_svg`, `intl` + JSON-asset i18n, `firebase_core` +
`firebase_messaging`, `google_sign_in` + `sign_in_with_apple`, `fl_chart` (new —
for AthleteHub's charts; lotuga didn't need it). **No** Riverpod/Bloc/Dio/Drift.

**`lib/` layout — organize by type:**

```
client/lib/
├── main.dart
├── firebase_options.dart
├── config/        # app_config.dart (env, base URL, feature flags)
├── i18n/          # app_localizations.dart (+ assets/i18n/en.json, pt.json)
├── models/        # plain Dart data classes w/ fromJson/toJson (manual)
│   └── responses/ # *_response.dart API envelope models
├── screens/       # one widget file per screen (feed, train, workout, diet, evolution, coach...)
├── services/      # business/IO services (auth, secure storage, notifications)
│   └── api/       # per-domain *_api_service.dart + http_interceptor.dart
├── styles/        # app_theme.dart (dark/light + accent palettes from design tokens)
└── widgets/       # reusable widgets (cards, chips, charts, inputs)
```

**Rules**
- **State:** plain `StatefulWidget` + `setState`. A screen calls an api service,
  holds the result in local state, shows loading/error/empty. No state-mgmt lib.
- **Networking:** `http` package wrapped in `services/api/*_api_service.dart`;
  `http_interceptor.dart` attaches the JWT and handles 401→refresh. Models parse
  JSON manually via `fromJson` (no codegen).
- **Models:** one class per resource in `models/`; API envelopes in
  `models/responses/`. Mirror lotuga's naming (`auth_response.dart`, etc.).
- **Theming:** `styles/app_theme.dart` builds `ThemeData` for dark/light and the
  accent palettes (volt/cyan/magenta/orange) from the design tokens.
- **Secrets/tokens:** `flutter_secure_storage`; config via `flutter_dotenv` (`.env`).
- **i18n:** JSON assets + `app_localizations.dart` (en + pt, like lotuga).
- **Charts:** `fl_chart` wrappers in `widgets/` for the line/bar/ring charts.
- **No** offline DB or sync engine in MVP (online-first; show loading states).
- **Tests:** `flutter_test` widget tests, `mockito` + `build_runner` for mocks,
  `integration_test` for happy-path flows (mirror lotuga).

## 5. Naming quick-reference

| Thing | Convention | Example |
|---|---|---|
| Java entity | PascalCase singular | `WorkoutSession.java` |
| Java repo | `<Entity>Repository` | `WorkoutSessionRepository.java` |
| Java service | `<Domain>Service` | `WorkoutService.java` |
| Java controller | `<Domain>Controller` | `WorkoutController.java` |
| Request/response DTO | `*Request` / `*Response` / `*Dto` | `LogWorkoutRequest.java` |
| Migration | `V<ts>__snake_desc.sql` | `V20260601120000__create_workouts.sql` |
| Dart screen | `snake_case_screen.dart` | `workout_screen.dart` |
| Dart api service | `<domain>_api_service.dart` | `training_api_service.dart` |
| Dart model | `snake_case.dart` | `workout_session.dart` |

When in doubt, **open the equivalent file in `lotuga` and copy its shape.**
