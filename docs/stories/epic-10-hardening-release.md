# EPIC 10 — Hardening & release

Cross-cutting quality and the path to shipping. Work these alongside features and
before launch.

---

## AH-100 — Rate limiting, CORS, consistent errors
**Acceptance criteria**
- [ ] `security/RateLimitingFilter` using bucket4j (tighter buckets on auth, search, write endpoints) → `429` + `Retry-After`.
- [ ] CORS locked to the app origins.
- [ ] All errors flow through the global advice with consistent JSON (verify across endpoints).

**Technical notes** — Mirror lotuga `security/RateLimitingFilter`.

## AH-101 — Observability
**Acceptance criteria**
- [ ] Actuator health/info/prometheus exposed; Micrometer metrics on key endpoints.
- [ ] Structured logging with a request correlation id; **no PII/health values in logs**.
- [ ] Basic dashboards/alerts documented (can defer the stack; ensure metrics exist).

## AH-102 — CI + containerization
**Acceptance criteria**
- [ ] GitHub Actions: backend job (`mvnw verify` with Testcontainers) + client job (`flutter analyze` + `flutter test`).
- [ ] `api/Dockerfile` builds a runnable image; compose brings up api + Postgres.
- [ ] Branch protection / PR checks green before merge.

**Technical notes** — Mirror lotuga `.github/workflows` + `api/Dockerfile`.

## AH-103 — Align legacy architecture docs to MVP
**Acceptance criteria**
- [ ] In `01,02,03,04,05,06,07,08`, mark or trim the sections that describe the superseded approach (modular monolith / hexagonal / outbox / Kafka / Redis / Riverpod / offline-first / GraphQL) with a pointer to [CONVENTIONS.md](../architecture/CONVENTIONS.md).
- [ ] `02-data-model.md` schema updated: single schema, drop `client_uuid`/offline columns, keep relational tables.
- [ ] README stack table + key decisions reflect the layered MVP.

**Technical notes** — CONVENTIONS.md stays the authoritative source; these docs become consistent background/reference. (03-backend & 05-frontend already updated in the same change that created the stories.)
