# AthleteHub

A gym training journal + fitness social network with a built-in coaching
platform. Athletes log workouts, cardio, body evaluations and diet, follow each
other and share activity; coaches manage rosters of athletes and assign
training, nutrition and check-ins.

**Mono-repo:**

| Path | What |
|------|------|
| `api/` | Backend — Java 25 + Spring Boot 4 (Maven), PostgreSQL + Flyway. Layered (`controller → service → repository`). |
| `client/` | Mobile app — Flutter (iOS + Android), type-based structure, plain `setState`. |
| `docs/` | Architecture (`docs/architecture/`) and the story backlog (`docs/stories/`). |

Conventions mirror the `lotuga` project. See
[`docs/architecture/CONVENTIONS.md`](docs/architecture/CONVENTIONS.md) (authoritative)
and resume work from [`docs/stories/BACKLOG.md`](docs/stories/BACKLOG.md).

## Run the backend (`api/`)

```bash
cd api
docker compose up -d            # start PostgreSQL
cp .env.example .env            # then fill in secrets
./mvnw spring-boot:run          # needs JDK 25
# health check: http://localhost:8080/actuator/health
```

Run backend tests (unit + Testcontainers integration):

```bash
cd api
./mvnw verify                   # needs Docker running for *IT tests
```

## Run the app (`client/`)

```bash
cd client
cp .env.example .env            # set API base URL
flutter pub get
flutter run
```

## Architecture & plan

- **How to build (authoritative):** [`docs/architecture/CONVENTIONS.md`](docs/architecture/CONVENTIONS.md)
- **Backlog / where we left off:** [`docs/stories/BACKLOG.md`](docs/stories/BACKLOG.md)
- **Background:** the rest of [`docs/architecture/`](docs/architecture/)
