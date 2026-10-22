# HouseHack

House-hacking agent for first-time buyers in Brevard County, FL. Big Data course project (Fall 2026).
Read `docs/course/project-brief.md` for scope and `docs/superpowers/specs/2026-10-21-house-hack-design.md` for the design.

## Rules
- Course rubric order first (C-level 7 items → B → A). `docs/course/assignments/rubric.md`.
- Every program rule (FHA, Hometown Heroes, HFA, SHIP, homestead) reads from the buyer profile and `program_rules(year)`. Nothing hard-coded to one person.
- Two numbers per listing, never blended: **buyer view** (100% rent − tunable reserves; the ranking) and **lender view** (FHA 75% rule, self-sufficiency; pass/fail).
- RentCast free tier = 50 requests/month. Collector polls daily and tracks `api_budget`. No on-demand external calls.
- Secrets only from env vars: `DATABASE_URL`, `RENTCAST_API_KEY`, later `SMTP_URL`. Never commit them.
- Three processes (web, collector, analyzer) locally; in Render production the web process also runs the schedulers via `RUN_WORKERS`.
- Dates absolute (YYYY-MM-DD). Cite sources with URL + access date.

## Build
- JDK 21 required (Gradle 8.7 / Kotlin 1.9 won't run on 25). On Ryan's PC set `JAVA_HOME` to the Temurin 21 install for this shell.
- `./gradlew clean build` · run jars from `applications/*/build/libs/`.
- Tests need local Postgres: `TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/househack_test`.
