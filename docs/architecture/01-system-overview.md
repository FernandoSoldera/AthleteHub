# 01 · System Overview

## 1. Context (who talks to AthleteHub)

```mermaid
graph TB
    subgraph Clients
        A[Athlete<br/>Flutter app]
        C[Coach<br/>Flutter app]
    end

    A & C -->|HTTPS / WSS| EDGE[API Gateway / Load Balancer]

    EDGE --> BE[AthleteHub Backend<br/>Spring Boot]

    BE --> PG[(PostgreSQL<br/>+ TimescaleDB)]
    BE --> RD[(Redis)]
    BE --> OBJ[(Object Storage + CDN)]
    BE --> MQ[(Kafka / broker)]

    BE -->|push| FCM[FCM / APNs]
    BE -->|OAuth| IDP[Apple / Google Sign-In]
    BE -. future .-> HEALTH[HealthKit / Google Fit /<br/>Strava / Garmin]
    BE --> MAIL[Email / Transactional]
```

Athletes and coaches use the **same Flutter app** in different modes (the design
switches `role` between `student` and `teacher`). There is no separate coach
client at launch.

## 2. Containers (the deployable pieces)

```mermaid
graph TB
    subgraph Mobile["Flutter app (iOS + Android)"]
        UI[Presentation / widgets]
        SM[State mgmt - Riverpod]
        REPO[Repositories]
        LOCAL[(Local DB - Drift/SQLite<br/>offline outbox)]
        UI --> SM --> REPO
        REPO --> LOCAL
    end

    REPO -->|REST JSON / WS| GW[Edge: API Gateway + WAF + LB]

    subgraph Backend["Backend (modular monolith, horizontally scaled)"]
        REST[REST API controllers]
        WS[WebSocket / STOMP gateway]
        APP[Application services<br/>per bounded context]
        EVT[Domain events + outbox]
        JOBS[Scheduled jobs / workers]
        REST --> APP
        WS --> APP
        APP --> EVT
        JOBS --> APP
    end

    GW --> REST
    GW --> WS

    APP --> PG[(PostgreSQL)]
    APP --> RD[(Redis)]
    APP --> OBJ[(Object Storage)]
    EVT --> MQ[(Kafka)]
    MQ --> CONS[Async consumers:<br/>feed fan-out, notifications,<br/>adherence, analytics]
    CONS --> PG
    CONS --> RD
    CONS --> PUSH[FCM / APNs]
```

### Container responsibilities

| Container | Responsibility |
|---|---|
| **Flutter app** | All UI, offline workout/eval logging, optimistic updates, local cache, sync engine, push handling |
| **Edge (Gateway/LB/WAF)** | TLS termination, routing, rate limiting, auth token check, request size limits |
| **Backend (Spring Boot)** | Business logic for all bounded contexts, persistence, event publication, realtime fan‑out |
| **Async consumers** | Run inside the same deployable for MVP (Spring `@KafkaListener` / `@Async`); extracted to separate workers as volume grows |
| **PostgreSQL** | System of record for all durable state |
| **Redis** | Refresh‑token store, cache, rate‑limit counters, feed timeline cache, presence, rest‑timer coordination |
| **Object storage + CDN** | Media (progress photos, evolution images, coach branding, exports) |
| **Kafka** | Durable async backbone between contexts |

## 3. Bounded contexts (the modules)

These map 1:1 to the screens in the design and to backend modules
([03-backend](03-backend.md)) and DB schemas ([02-data-model](02-data-model.md)).

```mermaid
graph LR
    subgraph Core
      ID[Identity & Access]
      SOC[Social / Graph]
      FEED[Feed]
    end
    subgraph Athlete
      TR[Training]
      BODY[Body / Evolution]
      NUT[Nutrition]
    end
    subgraph Coach
      COACH[Coaching]
      MSG[Messaging]
    end
    subgraph Platform
      NOTIF[Notifications]
      MEDIA[Media]
      INT[Integrations - future]
    end

    SOC --> FEED
    TR --> FEED
    BODY --> FEED
    COACH --> TR
    COACH --> NUT
    COACH --> BODY
    COACH --> MSG
    COACH --> NOTIF
    BODY --> NOTIF
```

| Context | Screens it powers |
|---|---|
| **Identity & Access** | Sign in / sign up, OAuth, role switch, account, password reset |
| **Social / Graph** | Find people, follow/unfollow, suggestions, profile header counts |
| **Feed** | Activity feed, filters, likes, comments, share |
| **Training** | Train tab, live workout session, cardio log, recent sessions, PRs |
| **Body / Evolution** | Evolution tab, new evaluation (manikin), graph detail, evaluations history |
| **Nutrition** | Diet tab, macro ring, meals, add food, food DB, daily diary, day strip |
| **Coaching** | Students dashboard, student detail, assign workout/diet/eval, schedule, library, coach profile, adherence/flags |
| **Messaging** | Coach inbox, athlete↔coach threads |
| **Notifications** | Push (eval reminders, "notify student"), in‑app notifications |
| **Media** | Photo upload/processing, signed URLs |
| **Integrations** *(future)* | HealthKit / Google Fit / Strava / Garmin sync for HR, power, runs |

## 4. End‑to‑end flows

### 4.1 Logging a workout (offline‑capable, the hardest path)

```mermaid
sequenceDiagram
    participant U as Athlete (phone)
    participant L as Local DB (Drift)
    participant Q as Sync outbox (phone)
    participant API as Backend REST
    participant DB as PostgreSQL
    participant MQ as Kafka
    participant FN as Feed/Notif consumers

    U->>L: Start session, complete sets (works offline)
    L-->>U: Optimistic UI (progress, volume, rest timer)
    Note over Q: When connectivity returns
    Q->>API: POST /workout-sessions (Idempotency-Key)
    API->>DB: Persist session, sets, compute volume/PRs (tx)
    API->>MQ: WorkoutCompleted event (via outbox)
    API-->>Q: 201 + canonical IDs
    Q->>L: Reconcile local rows with server IDs
    MQ->>FN: Fan-out to followers' feeds, fire "PR!" push
```

Key points: the **rest timer and set logging never depend on the network**; the
backend computes the *authoritative* volume/PR numbers; the
`Idempotency-Key` makes a retried POST safe; feed and push happen
**asynchronously** so the save is fast.

### 4.2 Coach assigns an evaluation check‑in

```mermaid
sequenceDiagram
    participant C as Coach
    participant API as Backend
    participant DB as PostgreSQL
    participant SCH as Scheduler
    participant P as Push (FCM/APNs)
    participant A as Athlete

    C->>API: POST /athletes/{id}/eval-requests (date, points, notes)
    API->>DB: Save eval_request + scheduled_notification (deliver_at = date - 24h)
    API-->>C: 201 Scheduled
    SCH->>DB: Poll due notifications
    SCH->>P: "Check-in tomorrow 08:00"
    P-->>A: Push received
    A->>API: Opens app, submits evaluation (18 measurements)
    API->>DB: Persist evaluation + measurements, recompute body-fat & graphs
    API-->>C: (async) notify coach "Alex submitted eval"
```

### 4.3 Reading the social feed

```mermaid
sequenceDiagram
    participant A as Athlete
    participant API as Backend (Feed)
    participant RD as Redis (timeline cache)
    participant DB as PostgreSQL

    A->>API: GET /feed?filter=all&cursor=...
    API->>RD: Lookup cached timeline page
    alt cache hit
        RD-->>API: post IDs
    else cache miss (MVP: fan-out-on-read)
        API->>DB: SELECT posts WHERE author IN (followees) ORDER BY created_at < cursor
        API->>RD: Cache page (short TTL)
    end
    API->>DB: Hydrate posts + author + like/comment counts + viewer "liked?"
    API-->>A: Page of feed cards + next cursor
```

The feed starts **fan‑out‑on‑read** (simple, correct, cheap) and moves to
**fan‑out‑on‑write** with a Redis/feed‑store timeline when read volume demands
it — see the scale triggers in [08-roadmap](08-roadmap.md).

## 5. Tech stack summary

**Backend:** Java 21, Spring Boot 3 (Web, Security, Data JPA, Validation),
Spring for GraphQL *(Phase 2)*, Spring WebSocket/STOMP, Resilience4j, Flyway,
Micrometer + OpenTelemetry, Testcontainers.

**Data:** PostgreSQL 16 + TimescaleDB, Redis 7, S3‑compatible storage, Kafka.

**Mobile:** Flutter 3 (stable), Riverpod, go_router, Dio, Drift (SQLite),
freezed + json_serializable, fl_chart, firebase_messaging, flutter_secure_storage.

**Platform:** Docker, Kubernetes *or* Cloud Run/ECS Fargate, GitHub Actions,
managed Postgres/Redis, CDN, secrets manager.
