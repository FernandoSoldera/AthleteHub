# AthleteHub — start here

AthleteHub is a gym training journal + fitness social network with a built-in
coaching platform (athletes log workouts/cardio/body-evaluations/diet, follow
each other, share activity; coaches manage athletes and assign training).

**Mono-repo:** `api/` (Spring Boot) + `client/` (Flutter) + `docs/`.

## Resuming work across sessions

1. **Backlog (where we left off):** [`docs/stories/BACKLOG.md`](docs/stories/BACKLOG.md)
   — pick the first `TODO` story in build order; open its epic file for
   acceptance criteria; update its status when done.
2. **How to build (authoritative):** [`docs/architecture/CONVENTIONS.md`](docs/architecture/CONVENTIONS.md)
   — layout, patterns, naming, testing.
3. **Background:** the rest of `docs/architecture/` (overview, data model, API, security).

## Conventions (non-negotiable for MVP)

- **Mirror the `lotuga` project** at `C:\Users\Fernando\Documents\git\lotuga`.
  When unsure how to structure something, open the equivalent file in lotuga and
  copy its shape.
- **Backend:** plain **layered** Spring Boot (`controller → service →
  repository` + `model`/`dto`/`config`/`security`/`scheduler`), single package
  `com.example.athletehub`. Java 25 / Spring Boot 4 / Maven / PostgreSQL /
  Flyway / Lombok / JWT. **No** hexagonal, bounded contexts, outbox, Kafka, or
  Redis for the MVP.
- **Frontend:** **type-based** Flutter (`screens`/`services`+`services/api`/
  `models`/`widgets`/`styles`), plain `setState`, `http` package. **No**
  Riverpod/Bloc/Dio/offline-DB for the MVP.
- **MVP first, online-first.** Earn complexity; don't pre-build it.

The older `docs/architecture/01,02,06,08` describe a heavier (modular-monolith /
hexagonal / offline-first) design that we **deliberately simplified** —
CONVENTIONS.md supersedes them (tracked in story AH-103).
