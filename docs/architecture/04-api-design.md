# 04 · API Design

## 1. Decision: REST or GraphQL?

> **REST‑first for MVP** (OpenAPI‑documented). **Add GraphQL as a read‑only
> BFF layer in Phase 2** for the aggregation‑heavy screens, *if* round‑trip
> count or over‑fetching becomes a measured problem. Realtime is **WebSocket /
> STOMP**, not GraphQL subscriptions.

### The honest trade‑off

GraphQL genuinely fits parts of this app. Several screens are **composite reads**
that stitch many entities together:

- **Feed card** = author + workout/cardio/eval summary + stats + chart + like/
  comment counts + "did I like it?"
- **Student detail** = profile + latest evals + bodyweight series + week plan +
  adherence + assign actions.
- **Profile / Coach profile** = user + counters + evolution highlights + recent
  posts grid + settings.

For these, GraphQL lets the Flutter app fetch exactly one tailored payload per
screen and evolve the UI without new backend endpoints. That's real value.

But GraphQL also brings cost that fights the "phased, small‑team, ship‑fast"
posture you chose:

| GraphQL cost | Impact here |
|---|---|
| N+1 resolver problem | Needs DataLoader batching everywhere or DB melts |
| Caching is hard | Loses cheap HTTP/CDN caching the feed would love |
| Query‑cost / depth limiting | Must be built to stop abusive queries |
| File upload & binary | Awkward; you still need REST for media anyway |
| Realtime | Subscriptions are heavier than a plain STOMP channel |
| Team ramp | One more paradigm on top of Spring + Flutter + offline sync |

### Verdict

- **Phase 1 (MVP): REST only.** Predictable, cacheable, trivially documented
  with OpenAPI, easy to secure and rate‑limit, plays well with the offline
  outbox (idempotent POST/PUT). Composite screens get **purpose‑built
  aggregate endpoints** (e.g. `GET /feed`, `GET /coach/athletes/{id}/overview`)
  — a "BFF in REST clothing." This removes 90% of GraphQL's perceived benefit at
  ~10% of the cost.
- **Phase 2: introduce GraphQL (Spring for GraphQL) as a read gateway** over the
  same application services, scoped to the heavy aggregation screens. Adopt it
  **only on a trigger**: client teams repeatedly blocked waiting for new bespoke
  endpoints, or measurable mobile over‑fetch on metered connections.
- **Never** route writes/uploads/realtime through GraphQL — keep those REST + WS.

**Trigger to adopt GraphQL:** ≥3 screens needing bespoke aggregate endpoints per
release *and* mobile payloads measurably bloated → stand up the read gateway.

---

## 2. REST conventions

- Base: `https://api.athletehub.app/v1` — version in the path.
- JSON, `camelCase`, `application/json; charset=utf-8`.
- Auth: `Authorization: Bearer <access_jwt>`.
- **Idempotency:** all `POST`/`PUT` that create client data accept
  `Idempotency-Key: <client_uuid>`; server dedupes on the matching unique
  constraint and returns the original result on replay.
- **Pagination:** cursor‑based (`?cursor=&limit=`), responses carry
  `{ data: [...], nextCursor }`. No offset pagination on feeds/lists.
- **Partial fields:** `?fields=` for trimming large objects (cheap GraphQL‑lite).
- **Filtering/sorting:** explicit query params per endpoint (`?type=workout`).
- **Errors:** RFC 9457 `application/problem+json`:

```json
{ "type":"https://athletehub.app/errors/validation",
  "title":"Validation failed", "status":422,
  "detail":"weightKg must be > 0", "instance":"/v1/evaluations",
  "errors":[{"field":"weightKg","code":"min"}] }
```

- **Rate limits:** per‑user + per‑IP at the gateway; `429` + `Retry-After`.

## 3. Endpoint catalog (MVP, by context)

### Identity & Access
```
POST   /auth/register
POST   /auth/login
POST   /auth/oauth/{apple|google}      # exchange provider token
POST   /auth/token/refresh             # rotate refresh -> new access
POST   /auth/logout
POST   /auth/password/forgot
POST   /auth/password/reset
GET    /me                             # profile + roles + active role
PATCH  /me                             # bio, name, height, avatar hue
POST   /me/roles/switch                # student <-> teacher (must hold role)
DELETE /me                             # GDPR erase (async)
```

### Social
```
GET    /users/{handle}                 # public profile aggregate
GET    /users/search?q=                # by name/@handle
GET    /users/suggestions              # "suggested for you" + mutual counts
POST   /users/{id}/follow
DELETE /users/{id}/follow
GET    /me/followers ?cursor
GET    /me/following ?cursor
```

### Feed
```
GET    /feed ?filter=all|workout|run|cycle|evolution &cursor   # home timeline
POST   /posts                          # manual post (most posts are event-generated)
GET    /posts/{id}
DELETE /posts/{id}
POST   /posts/{id}/like
DELETE /posts/{id}/like
GET    /posts/{id}/comments ?cursor
POST   /posts/{id}/comments
GET    /posts/{id}/share               # shareable link / payload
```

### Training
```
GET    /training/today                 # today's plan hero
GET    /exercises?q=                    # catalog search
GET    /workout-templates              # mine + assigned
POST   /workout-sessions               # start (Idempotency-Key)
PATCH  /workout-sessions/{id}          # append/complete sets, rest events
POST   /workout-sessions/{id}/finish   # finalize -> volume/PR computed
GET    /workout-sessions ?cursor       # recent sessions
POST   /cardio-activities              # log run/walk/cycle (+ samples batch)
GET    /cardio-activities ?cursor
GET    /training/weekly-summary        # this-week cardio km, etc.
```

### Body / Evolution
```
GET    /evaluations ?cursor
POST   /evaluations                    # eval + measurements (Idempotency-Key)
GET    /evaluations/{id}
GET    /body/metrics/{metric}?range=4w|12w|6m|1y   # series for graph detail
GET    /body/overview                  # hero weight + 3-up stats + graph rows
```

### Nutrition
```
GET    /diet/plan/active
GET    /diet/day?date=                  # meals + items + totals + remaining
POST   /diary-entries                   # add food to a meal/day (Idempotency-Key)
DELETE /diary-entries/{id}
GET    /foods/search?q=
GET    /foods/barcode/{code}            # Phase 2
GET    /foods/favorites
POST   /foods                           # create custom food
```

### Coaching
```
GET    /coach/athletes ?flag=           # roster + adherence + flags (dashboard)
GET    /coach/overview                  # summary tiles
GET    /coach/athletes/{id}/overview    # student detail aggregate
POST   /coach/athletes/{id}/assign/workout
POST   /coach/athletes/{id}/assign/diet
POST   /coach/athletes/{id}/eval-requests
GET    /coach/schedule?week=
GET    /coach/library?kind=workout|exercise|diet
POST   /coach/invitations               # invite/link an athlete (consent)
POST   /coach/invitations/{id}/accept   # athlete accepts -> relationship active
```

### Messaging
```
GET    /conversations ?cursor
GET    /conversations/{id}/messages ?cursor
POST   /conversations/{id}/messages    # also delivered via WS (Idempotency-Key)
POST   /conversations/{id}/read        # advance read pointer
```

### Notifications, Devices, Media
```
POST   /devices                        # register push token
DELETE /devices/{id}
GET    /notifications ?cursor
POST   /notifications/read
POST   /media/upload-url               # -> signed PUT URL + media_id
POST   /media/{id}/complete            # mark uploaded; triggers processing
```

## 4. Aggregate endpoints (the "BFF in REST clothing")

These collapse a screen into one round trip so the client stays snappy on
mobile networks — exactly the pain GraphQL would solve, solved cheaply:

| Screen | Endpoint | Returns |
|---|---|---|
| Feed | `GET /feed` | hydrated cards (author, summary, stats, chart, counts, liked?) |
| Train tab | `GET /training/today` + `GET /training/weekly-summary` | plan hero + cardio chart + recent |
| Evolution | `GET /body/overview` | hero weight, 3‑up deltas, graph rows, recent evals |
| Graph detail | `GET /body/metrics/{m}?range=` | series + highlights |
| Diet | `GET /diet/day` | macro totals, ring, meals, items, remaining |
| Profile | `GET /users/{handle}` | header, counters, evolution highlights, posts grid |
| Student detail | `GET /coach/athletes/{id}/overview` | profile, evals, weight series, week plan, adherence |

## 5. Realtime (WebSocket / STOMP)

Single authenticated WS connection (JWT in CONNECT). Topics:

| Destination | Purpose |
|---|---|
| `/user/queue/notifications` | live in‑app notifications (PR, assignment, eval reminder) |
| `/topic/conversation.{id}` | chat messages + typing/read receipts |
| `/user/queue/coach.feed` | coach gets "athlete finished session" live |
| `/topic/session.{id}` (opt) | multi‑device live workout session mirroring |

Presence (online/last‑seen) lives in Redis with TTL. Push (FCM/APNs) is the
fallback when the socket is closed. The **rest timer is purely client‑side** —
no server round trip.

## 6. Versioning, compatibility & deprecation

- URL versioning (`/v1`). Additive changes (new fields) are non‑breaking;
  clients ignore unknown fields. Breaking changes ship under `/v2` with an
  overlap window.
- **Mobile can't be force‑updated**, so the API supports the **two most recent
  app versions**; a `Min-App-Version` response header drives a soft‑update
  nudge and a hard‑block for unsupported clients.
- OpenAPI spec is the contract; the Flutter client generates DTOs from it.
