# EPIC 0 — Foundations

Get the mono-repo and both projects scaffolded so feature work can start. Mirror
[`lotuga`](../../../lotuga) and follow [CONVENTIONS.md](../architecture/CONVENTIONS.md).

> **Story format:** each story has a user-value line, **Acceptance criteria**
> (checkboxes — tick when met), and **Technical notes** (how, with lotuga refs).
> Mark the story `DONE` in [BACKLOG.md](BACKLOG.md) when all boxes are checked.

---

## AH-001 — Initialize the mono-repo
So that all code and docs live in one versioned place with a clear layout.

**Acceptance criteria**
- [ ] `git init` at `athletehub/`; sensible `.gitignore` (Java/Maven, Flutter/Dart, `.env`, IDE, build dirs — copy from lotuga's two `.gitignore`s).
- [ ] Top-level layout `api/`, `client/`, `docs/` exists.
- [ ] Architecture docs flattened: move `AthleteHub/docs/*` → `docs/` at repo root; remove the redundant `AthleteHub/` folder.
- [ ] Root `README.md` describing the repo (what AthleteHub is, how to run api + client, link to `docs/architecture`).

**Technical notes**
- After flattening, paths become `docs/architecture/...` and `docs/stories/...`.
- Keep `.claude/settings.local.json` at root.

---

## AH-002 — Scaffold the Spring Boot backend (`api/`)
So that we have a runnable backend with DB migrations and config.

**Acceptance criteria**
- [ ] `api/pom.xml` mirrors lotuga's: Spring Boot 4.0.0 parent, Java 25, starters (web, security, data-jpa, validation, mail, actuator, oauth2-client), PostgreSQL + Flyway, JJWT, Lombok, micrometer-prometheus, bucket4j, firebase-admin, spring-dotenv; test deps (test, security-test, h2, testcontainers postgres + junit-jupiter, greenmail, awaitility, wiremock); failsafe + surefire plugins.
- [ ] Base package `com.example.athletehub` with empty layer packages: `config controller dto enums exception model repository scheduler security service util`.
- [ ] `@SpringBootApplication` main class boots.
- [ ] `application.yml` with `dev`/`test`/`prod` profiles; datasource from env; Flyway enabled.
- [ ] `.env.example` (+ local `.env`), `Dockerfile`, `docker-compose.yml` running PostgreSQL (copy lotuga shapes).
- [ ] Flyway baseline migration `V<ts>__baseline.sql` (can be empty/extensions) runs clean; `mvnw spring-boot:run` starts and `/actuator/health` is UP.

**Technical notes**
- Reference: `lotuga/api/pom.xml`, `docker-compose.yml`, `src/main/resources/application*.yml`, `config/`.
- Enable `btree_gist`/extensions later only if a schema needs them.

---

## AH-003 — Scaffold the Flutter client (`client/`)
So that we have a runnable app shell with theming, i18n, and the folder structure.

**Acceptance criteria**
- [ ] `flutter create` in `client/`; `pubspec.yaml` adds: `http`, `flutter_secure_storage`, `flutter_dotenv`, `flutter_svg`, `intl`, `flutter_localizations`, `firebase_core`, `firebase_messaging`, `google_sign_in`, `sign_in_with_apple`, `fl_chart`; dev: `flutter_test`, `integration_test`, `mockito`, `build_runner`, `flutter_lints`.
- [ ] `lib/` folders created: `config i18n models models/responses screens services services/api styles widgets`.
- [ ] `config/app_config.dart` reads base URL from `.env` (+ `.env.example`).
- [ ] `styles/app_theme.dart` builds dark + light `ThemeData` with the 4 accent palettes (volt/cyan/magenta/orange) from the design tokens.
- [ ] `i18n/app_localizations.dart` + `assets/i18n/en.json`, `pt.json` wired (mirror lotuga).
- [ ] `main.dart` boots an app shell with bottom tabs (Feed/Train/Evolve/Diet/Me placeholders) and the theme applied; `flutter run` works.

**Technical notes**
- Reference: `lotuga/client/pubspec.yaml`, `lib/styles/app_theme.dart`, `lib/i18n/`, `lib/main.dart`, `lib/config/app_config.dart`.
- Design tokens are in the original handoff `tokens.css`; map them into `app_theme.dart`.

---

## AH-004 — Backend cross-cutting plumbing
So that every later endpoint has consistent responses, errors, and a test harness.

**Acceptance criteria**
- [ ] `dto/ApiResponse<T>` envelope (mirror lotuga `dto/ApiResponse.java`).
- [ ] `exception/` global `@RestControllerAdvice` returning consistent error JSON (validation, not-found, auth, generic).
- [ ] `config/WebConfig` (CORS for the app), `config/JacksonConfig` (JSR-310, snake/camel as lotuga).
- [ ] `security/SecurityConfig` skeleton (stateless, permit `/actuator/health` + auth endpoints; everything else authenticated — real rules arrive in EPIC 1).
- [ ] Integration-test harness: a base `*IT` class spinning up Testcontainers PostgreSQL so Flyway runs; one smoke test hitting `/actuator/health`.

**Technical notes**
- Reference: lotuga `dto/ApiResponse.java`, `exception/`, `config/WebConfig.java`, `security/SecurityConfig.java`, and its Testcontainers IT base class.
