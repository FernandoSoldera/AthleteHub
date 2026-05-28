# AthleteHub — System Architecture

> **AthleteHub** is a training journal + fitness social network with a built‑in
> coaching platform. Athletes log workouts, cardio, body evaluations and diet;
> they follow each other and share activity; coaches manage rosters of athletes
> and assign training, nutrition and check‑ins.

> ⚠️ **MVP approach update (authoritative): see [CONVENTIONS.md](CONVENTIONS.md).**
> We build the MVP as a **plain layered backend + type-based Flutter app**,
> mirroring the `lotuga` project — **not** the modular‑monolith / hexagonal /
> offline‑first / event‑driven design some sections below still describe. Where
> these docs conflict with CONVENTIONS.md, **CONVENTIONS.md wins.** The work is
> broken into stories in [../stories/BACKLOG.md](../stories/BACKLOG.md).

## Target stack (MVP)

| Layer | Choice | Why |
|---|---|---|
| Repo | **Mono‑repo** (`api/` + `client/` + `docs/`) | Matches lotuga; one versioned place |
| Mobile | **Flutter** (iOS + Android), type‑based folders, plain `setState`, `http` | Matches design + lotuga; simplest that ships |
| Backend | **Java 25 + Spring Boot 4**, layered (`controller→service→repository`) | Matches lotuga; no premature modularization |
| Primary DB | **PostgreSQL** + Flyway | Domain is relational; see [02-data-model](02-data-model.md) |
| Auth | **JWT** (access + refresh) + OAuth2 social (Apple/Google) | Mirror lotuga `security/` |
| Push | **Firebase** (FCM/APNs) | Mirror lotuga |
| API style | **REST only** (JSON, `http` client) | No GraphQL for MVP — see [04-api-design](04-api-design.md) |

Deferred to post‑MVP (originally in these docs): Redis, Kafka/outbox, TimescaleDB,
object‑storage CDN at scale, WebSocket realtime, GraphQL, offline sync. Add on a
measured trigger.

## Build posture

**Layered MVP, online‑first, single deployable.** One Spring Boot app organized by
technical layer, one Flutter app organized by type, one PostgreSQL database. We
match an existing working codebase (`lotuga`) rather than impose a textbook
architecture — see [CONVENTIONS.md](CONVENTIONS.md). Refactor toward modules only
if/when the domain or team size earns it.

## The three questions this design answers

1. **SQL or NoSQL?** → **PostgreSQL** as the single system of record (+ Flyway).
   Reasoning + schema in [02-data-model](02-data-model.md). *(MVP drops the
   TimescaleDB/JSONB‑heavy parts; keep the relational tables.)*
2. **Is GraphQL worth it?** → **No (REST only for MVP).** See [04-api-design](04-api-design.md).
3. **Anything to plan on the frontend?** → **Yes** — theming (dark/light + 4
   accents), charts (`fl_chart`), i18n, secure token storage + refresh
   interceptor. *(Offline‑first/Riverpod is deferred; MVP is online‑first with
   plain `setState`.)* See [05-frontend-flutter](05-frontend-flutter.md).

## Document map

| Doc | Covers | MVP status |
|---|---|---|
| **[CONVENTIONS.md](CONVENTIONS.md)** | **Authoritative** build conventions (layout, layered/type‑based patterns, naming, testing) mirroring lotuga | ✅ current |
| [../stories/BACKLOG.md](../stories/BACKLOG.md) | The resumable story backlog (epics 0–10) | ✅ current |
| [01-system-overview](01-system-overview.md) | Context & container diagrams, contexts, flows | background (simplify per CONVENTIONS) |
| [02-data-model](02-data-model.md) | Relational schema + ERD | basis (drop offline/timescale bits) |
| [03-backend](03-backend.md) | Layered Spring Boot structure | ✅ updated to layered |
| [04-api-design](04-api-design.md) | REST decision, endpoint catalog | mostly current (REST) |
| [05-frontend-flutter](05-frontend-flutter.md) | Type‑based Flutter structure | ✅ updated to layered |
| [06-infrastructure-resilience](06-infrastructure-resilience.md) | Deploy, scaling, resilience | post‑MVP reference |
| [07-security-privacy](07-security-privacy.md) | AuthN/Z, privacy, threat model | current (right‑size for MVP) |
| [08-roadmap](08-roadmap.md) | Phased plan | superseded by the story backlog |

## Architecture principles (MVP)

1. **Match conventions; keep it simple.** Mirror `lotuga`; layered backend,
   type‑based client. Earn complexity, don't pre‑pay it.
2. **PostgreSQL until it hurts.** One database is a feature.
3. **Online‑first for MVP.** Clear loading/error/empty states; offline sync is a
   later upgrade.
4. **Server owns the truth.** Volume, body‑fat %, macro totals, adherence are
   computed server‑side.
5. **Health data is sensitive.** Body fat, weight, photos are special‑category;
   privacy and consent are designed in.
