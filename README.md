# HouseHack

A house-hacking agent for first-time buyers in Brevard County, FL. It collects active 2–4 unit listings every day, enriches them with county parcel data, runs them through the buyer's own profile (income, savings, credit, commute, assistance-program eligibility), and ranks them by **what you would actually pay each month after rent**. It also shows the lender's FHA view separately so you know whether the loan would be approved.

Built for the Fall 2026 Big Data course on the [application continuum](https://www.appcontinuum.io/) Kotlin/Ktor starter: one web application plus two background workers sharing a PostgreSQL database.

- Product brief, research with citations, rubric and assignments: [`docs/course/`](docs/course/)
- Design: [`docs/superpowers/specs/2026-10-21-house-hack-design.md`](docs/superpowers/specs/2026-10-21-house-hack-design.md)
- Architecture diagram: [`docs/course/assignments/01-whiteboard/architecture.svg`](docs/course/assignments/01-whiteboard/architecture.svg)

## How it works

| Process | Module | Job |
|---|---|---|
| `basic-server` (web) | `applications/basic-server` | Profile form, ranked report, listing detail, JSON API (`/api/profile`, `/api/listings`), `/health` |
| `data-collector-server` | `applications/data-collector-server` | Daily: RentCast listings search (≤ 50 calls/month budget), Florida parcel import, per-listing rent estimates |
| `data-analyzer-server` | `applications/data-analyzer-server` | Every minute: scores listings whose inputs changed; buyer view + lender view + program eligibility |

Components: `profile`, `listings-data` (repositories), `rentcast-client`, `parcel-import` (Florida DOR cadastral API), `data-collector`, `data-analyzer`, `wiring`. Support: `database-support` (HikariCP + Flyway), `workflow-support` (schedulers), `logging-support`.

The collector and analyzer never call each other; the analyzer's queue is "active listings whose listing, rent estimate, parcel, or profile changed since they were last analyzed."

## Local setup (Windows)

Requirements: Temurin **JDK 21** (`winget install EclipseAdoptium.Temurin.21.JDK`) and **PostgreSQL 16** with a role `househack` / password `househack` that can create databases.

```powershell
.\scripts\dev.ps1               # sets JAVA_HOME, creates househack + househack_test, builds, runs all tests
.\scripts\dev.ps1 -Run web      # http://localhost:8888  (also: -Run collector, -Run analyzer)
```

Environment variables:

| Variable | Used by | Notes |
|---|---|---|
| `DATABASE_URL` | all apps | JDBC URL or Render/Heroku `postgres://user:pass@host/db` |
| `TEST_DATABASE_URL` | tests | defaults to `jdbc:postgresql://localhost:5432/househack_test?user=househack&password=househack` |
| `RENTCAST_API_KEY` | web (with workers), collector | optional; without it the app uses six canned Brevard listings |
| `RUN_WORKERS` | web | `collector,analyzer` runs both schedulers inside the web process (Render free tier) |
| `PORT` | all apps | defaults 8888 / 8886 / 8887 |

Manual build: `./gradlew clean build`, then `java -jar applications/basic-server/build/libs/basic-server.jar`.

## Docker

```bash
./gradlew clean build
docker build -t househack .
RENTCAST_API_KEY=... docker compose up
```

## Deploy (Render, free tier)

1. Push `main`. In Render choose **New → Blueprint** and point it at this repo; [`render.yaml`](render.yaml) creates `househack-web` (Docker) and `househack-db` (Postgres).
2. Set `RENTCAST_API_KEY` in the service's environment (never commit it).
3. Verify `https://<service>.onrender.com/health` returns `"status": "ok"`, then open `/profile`.

The free Postgres expires after 30 days: create a new one, point `DATABASE_URL` at it, redeploy; Flyway recreates the schema and the 2026 program rules.

## Rubric mapping

| Item | Where |
|---|---|
| Web application (form, reporting) | `/profile`, `/report`, `/listings/{id}` |
| Data collection | `components/data-collector`, `components/parcel-import` |
| Data analyzer | `components/data-analyzer` |
| Unit tests | every component (`./gradlew test`) |
| Data persistence | PostgreSQL via `support/database-support` (Flyway `V1`, `V2`) |
| REST collaboration | `/api/*` JSON endpoints; external RentCast and ArcGIS REST APIs |
| Production environment | Render (`render.yaml`) |
| Integration tests | Ktor `testApplication` against a real Postgres (`applications/*/src/test`) |
| Test doubles | `FakeRentCastClient`, `FakeParcelSource`, Ktor `MockEngine`, fixed `Clock` |
| Continuous integration | `.github/workflows/ci.yml` (Gradle build with a Postgres service) |
| Monitoring | `/health` on every process, structured logging |
| Event messaging, continuous delivery | planned (A-level) |

## Caveats

Estimates only; not lending or tax advice. Drive times are straight-line estimates. Property tax uses a county-average millage until per-district rates are loaded. Program rules are 2026 Brevard values and are parameterized by year in `program_rules`.
