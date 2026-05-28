# 08 · Delivery Roadmap & Scale Triggers

> Phased plan matching the chosen posture: ship a solid modular monolith, then
> evolve on **measured triggers** — never adopt heavy infrastructure on a hunch.

## Phase 0 — Foundations (weeks 0–3)

Set up the rails so feature work is fast and safe.

- Monorepo (or two repos: `athletehub-api`, `athletehub-app`), CI/CD, Terraform.
- Spring Boot multi‑module skeleton + ArchUnit boundary tests; Flyway baseline.
- Flutter app shell: theme/tokens (dark/light + 4 accents), go_router, Riverpod,
  Drift DB, Dio client, design components (`AhCard`, charts, tabs).
- Auth end‑to‑end: email + Apple + Google, JWT + refresh, secure storage.
- Observability + health checks + staging environment.

**Exit:** a signed‑in user sees an empty themed shell on a real device.

## Phase 1 — MVP (weeks 4–14)

Everything in the design, REST‑only, single deployable, offline‑first client.

| Area | Scope |
|---|---|
| Identity/Social | profiles, follow, find people, suggestions, role switch |
| Feed | event‑generated posts (workout/run/cycle/evolution), like, comment, filters, fan‑out‑on‑read |
| Training | today's plan, **offline live session** (sets/rest timer/volume/PR), cardio log + samples, recent sessions |
| Body | evaluation (manikin: 13 circ + 8 skinfold), body‑fat calc, metric graphs, history |
| Nutrition | diet plan, meals, food DB + search, add food, daily diary, macro ring/remaining |
| Coaching | roster + adherence/flags, student detail, assign workout/diet/eval, schedule, library, coach profile, **consent/invite** |
| Messaging | coach↔athlete 1:1 chat, inbox, unread |
| Notifications | push (FCM/APNs), eval reminders (24h), in‑app inbox |
| Media | progress photo upload (signed URL), thumbnails |
| Platform | events in‑process + outbox, managed Postgres/Redis/queue, blue‑green deploy |

**Exit:** an athlete trains offline and syncs; a coach manages a roster and
assigns programs; both get push + chat. Private beta (TestFlight / Play
Internal).

## Phase 2 — Scale & polish (post‑launch, trigger‑driven)

Adopt each item **only when its trigger fires**:

| Upgrade | Trigger |
|---|---|
| **Kafka** (from SQS/Pub‑Sub) | event volume/replay/ordering needs grow; >1 consumer per event |
| **Fan‑out‑on‑write** `feed_entries` + Redis timelines | feed read p95 > 300ms or DB read load high |
| **OpenSearch** for food/people search | `tsvector` latency/relevance inadequate |
| **GraphQL read gateway** (Spring for GraphQL) | ≥3 bespoke aggregate endpoints/release **and** measured mobile over‑fetch |
| **Read replicas + PgBouncer** scale‑out | read traffic saturates primary |
| **Extract Notifications / Feed services** | independent scaling or deploy cadence needed |
| Barcode food scanning, MFA, data export/erase UI | product/compliance demand |

## Phase 3 — Ecosystem & reach (later)

- **Device/health integrations** (HealthKit, Google Fit, Strava, Garmin) →
  `connected_accounts`, import HR/power/runs, dedupe against manual logs.
- **Multi‑region** (read region + global DB) when user geography demands it.
- **Coach monetization** (payouts/branding already hinted in design), ratings,
  marketplace.
- **Web coach dashboard** (not in current scope) if coaches ask for big‑screen
  program building — the REST/GraphQL API already supports it.
- Document/wide‑column store for chat **only** if messaging tables get hot.

## Cross‑cutting "definition of done" per feature

1. REST endpoint + OpenAPI doc + validation + authz policy + audit where needed.
2. Domain event emitted via outbox where other contexts care.
3. Flutter: offline behavior + optimistic UI + sync/conflict handling.
4. Tests: unit + integration (Testcontainers) + widget/golden; sync path tested.
5. Metrics + traces + alerts; no PII in logs.
6. Privacy: consent respected, sensitive data encrypted, erasable.

## Team & ownership (suggested)

| Stream | Owns |
|---|---|
| Platform/Infra | CI/CD, IaC, observability, gateway, data stores |
| Backend — Athlete | identity, social, feed, training, body, nutrition |
| Backend — Coach | coaching, messaging, notifications, media |
| Mobile | Flutter app, offline sync engine, design system |
| (Shared) | API contract (OpenAPI) is co‑owned — it's the integration seam |

---

### One‑paragraph summary for stakeholders

AthleteHub launches as a single, well‑structured Spring Boot service backed by
PostgreSQL, Redis and object storage, with a Flutter app that works offline and
syncs in the background. The data model is relational because the domain
(training programs, social graph, coach–athlete relationships, nutrition) is
relational; non‑relational stores are added surgically for caching, search and
high‑volume feeds as the user base grows. The API is REST today and gains a
GraphQL read layer only if the mobile team's needs justify it. Resilience comes
from idempotent writes, a transactional outbox, async event processing, circuit
breakers and multi‑AZ managed infrastructure — and the module boundaries mean we
can split out the busiest parts into services without a rewrite.
