# 03 · Backend (Java + Spring Boot) — MVP layered

> Updated for the MVP. The authoritative, detailed conventions live in
> [CONVENTIONS.md](CONVENTIONS.md) §3. This doc is the rationale + the map.
> We mirror the `lotuga` backend: a **plain layered Spring Boot app**, not a
> modular monolith with bounded contexts.

## 1. Shape: one layered Spring Boot app

A single Spring Boot deployable organized **by technical layer**, exactly like
lotuga. No bounded-context modules, no per-context schemas, no domain-event bus,
no outbox/Kafka. Cross-feature work is just a service calling another service.

Why: for an MVP this is the fastest path to shipping, it matches a working team
codebase we trust, and a clean layered app refactors into modules later if the
domain ever earns that. The discipline that matters now is **separation of
concerns** (`controller ≠ service ≠ repository`), not module ceremony.

**Stack:** Java 25, Spring Boot 4.0.0, Maven, PostgreSQL, Flyway, Lombok, Spring
Security + JJWT + OAuth2 client, Spring Mail, Actuator + Micrometer/Prometheus,
bucket4j, Firebase Admin. Base package `com.example.athletehub`.

## 2. Package layout (by layer)

```
com.example.athletehub
├── config/        # Web, Jackson, Scheduling, Firebase, Database
├── controller/    # @RestController — thin: validate, call service, return DTO
├── dto/           # *Request / *Response / *Dto + ApiResponse<T>
├── enums/
├── exception/     # custom exceptions + @RestControllerAdvice
├── model/         # @Entity JPA classes
├── repository/    # Spring Data JPA interfaces
├── scheduler/     # @Scheduled jobs (eval reminders, adherence recompute)
├── security/      # JwtUtil, JwtAuthenticationFilter, SecurityConfig, RateLimitingFilter, ...
├── service/       # @Service — business logic + @Transactional
└── util/
```

Layering rule: **`controller → service → repository`.** Controllers never touch
repositories/entities directly; services own transactions and return DTOs;
entities never get serialized to JSON. (Mirror lotuga exactly.)

## 3. Features (what the services cover)

Same domain as before, now implemented as plain service classes (one per area),
not isolated contexts:

| Area | Key services | Stories |
|---|---|---|
| Identity & auth | `AuthService`, `RefreshTokenService`, `SocialAuthService`, `EmailService` | EPIC 1 |
| Social/profile | `FollowService`, `ProfileService` | EPIC 2 |
| Training | `WorkoutService`, `CardioService`, `ExerciseService` (volume/PR computed here) | EPIC 3 |
| Body/evolution | `EvaluationService` (body-fat calc in `util/`) | EPIC 4 |
| Nutrition | `FoodService`, `DietService`, `DiaryService` | EPIC 5 |
| Feed | `FeedService.publish(...)` (called directly by training/body services) | EPIC 6 |
| Coaching | `CoachService`, `AssignmentService` (+ `canCoachAccess` policy) | EPIC 7 |
| Messaging | `ConversationService`, `MessageService` | EPIC 8 |
| Notifications | `NotificationService` (push via Firebase) | EPIC 9 |

## 4. Cross-feature communication (MVP: direct calls)

No event bus. When an athlete finishes a workout, `WorkoutService` directly calls
`FeedService.publish(...)`, `CoachService.bumpAdherence(...)`, and
`NotificationService.sendPush(...)` within/after its transaction. Simple, easy to
trace, good enough for MVP volume. (A domain-event/outbox layer is a documented
post-MVP upgrade if these couplings get unwieldy.)

## 5. Scheduled jobs (`scheduler/`)

Plain `@Scheduled` methods (single instance for MVP, so no distributed lock
needed yet): eval-reminder dispatch (24h before an `eval_request`), nightly
coach adherence/flag recompute, denormalized-counter reconciliation.

## 6. Consistency & rules

- A workout/eval/diet save is one DB transaction; authoritative numbers (volume,
  body-fat %, macro totals, adherence) are computed **server-side**.
- Online-first: no client idempotency keys / `client_uuid` for MVP (revisit if
  offline support is added).
- DTOs at the edge; Bean Validation on requests; consistent error JSON via the
  global advice.

## 7. Testing (mirror lotuga)

`*Test` unit/slice (Surefire, H2) + `*IT` integration (Failsafe) on Testcontainers
PostgreSQL so Flyway runs as in prod; GreenMail for email, WireMock for external
APIs, Awaitility for async, Spring Security test for auth.
