# HouseHack — design

Date: 2026-10-21. Status: approved in conversation; builder-checked.
Course context and research: `docs/course/` in this repo (project-brief.md, research/, assignments/).

## Intent
A first-time buyer in Brevard County, FL wants to buy a 2–4 unit property near Cape Canaveral, live in one unit, and rent the rest. Listing sites show price; they do not show what *this buyer* would pay each month after rent, financing, taxes, insurance, and assistance programs, nor whether a lender would approve it. HouseHack collects listings daily, enriches them with county data, runs them through the buyer's profile, and ranks them by **net monthly housing cost**. It also reports the lender's math separately.

This is also the semester project for a Big Data course. The rubric (13 items, C→A) is satisfied in order; depth comes after each rubric item is checked off.

## Goals
- Rank every active 2–4 unit listing within the buyer's commute radius by net monthly housing cost, with a full, inspectable breakdown of every assumption.
- Show the lender view (FHA 75% rental-income rule; self-sufficiency test for 3–4 units) as a separate pass/fail with numbers.
- Check eligibility for Florida Hometown Heroes, Brevard HFA, Brevard SHIP, and the homestead exemption from profile fields; show cash-to-close after each program that applies.
- Stay inside RentCast's free tier (50 requests/month).
- Every rule parameterized by profile and by year; adding a second user or a second county is data, not code.

## Non-goals (v1)
- AI/LLM features. Planned as A-level extension (per-listing plain-English summary).
- Single-family + ADU / room-by-room renting. Rehab loans (203k).
- Counties other than Brevard. Multiple users with login (single profile row in v1; schema allows many).
- Mortgage rate lookup. Rate is a profile field with a default.

## Anti-goals
- Blending lender math into the ranking number. The two views are always separate.
- Hard-coding Ryan's numbers anywhere outside the seed profile.
- Scraping Zillow or any site that forbids it.
- On-demand "refresh" buttons that trigger external API calls.

## Constraints
- Kotlin 1.9 / Ktor 2.3 / Gradle 8.7 / JDK 21 as in the starter. Freemarker templates. JUnit (kotlin-test-junit).
- Three processes: `basic-server` (web), `data-collector-server`, `data-analyzer-server`, sharing one Postgres. Workers use the starter's `WorkScheduler`/`WorkFinder`/`Worker`.
- Deploy to Render free tier: 1 web service running the app plus both schedulers in-process (`RUN_WORKERS` env var), 1 free managed Postgres. Single Dockerfile; `APP` env var picks the jar. Three separate processes locally/in compose. See "Resolved after builder check".
- Secrets only via env: `DATABASE_URL`, `RENTCAST_API_KEY`, later `SMTP_URL`.
- Windows dev machine; no Docker locally. Local Postgres 16.

## Architecture
See `docs/assignments/01-whiteboard/architecture.svg`. Summary:

```
browser ──HTTP──► basic-server ──JDBC──► Postgres ◄──JDBC── data-collector ──HTTPS──► RentCast API
                  (pages + /api/*)          ▲                              └──HTTPS──► BCPAO bulk data
                                            └────JDBC──── data-analyzer ──SMTP(later)──► email
```
Collector and analyzer never talk to each other; they collaborate through the database (listings rows with `analyzed_at IS NULL` are the work queue).

### Gradle modules (additions to the starter)
- `components/listings-data` — repositories + migrations shared by all three apps (Postgres via HikariCP + plain JDBC; Flyway for migrations).
- `components/rentcast-client` — `RentCastClient` interface + HTTP impl (Ktor client) + `FakeRentCastClient` for tests. Exposes `searchSaleListings(area, unitsMin, unitsMax)`, `rentEstimate(address, beds, baths, sqft)`.
- `components/bcpao-import` — downloads `WebData.zip`, parses the parcel table, upserts `parcel_facts`. Stream-parse; never load 225 MB into memory. **Required for C-level:** RentCast sale listings carry no unit count; `parcel_facts.units` (from BCPAO land-use/living-units) is the source of truth, with `ceil(bedrooms / 2)` as a flagged fallback when no parcel matches.
- `components/data-collector` — `CollectListingsWorker` (daily), `ImportParcelsWorker` (daily), `FetchRentEstimatesWorker` (for listings with no estimate; budget-capped).
- `components/data-analyzer` — pure-Kotlin `FinancingCalculator`, `ProgramRuleEngine`, `ListingScorer`; `AnalyzeListingWorker`.
- `components/profile` — `BuyerProfile` data class + repository + validation.
- `applications/*` — wire-up only.

### Data model (Postgres)
```
profiles(id, name, annual_income, liquid_savings, credit_score, first_time_buyer bool,
         hth_eligible_occupation bool, veteran bool, household_size, workplace_lat, workplace_lng,
         max_drive_minutes, units_min, units_max, vacancy_rate, maintenance_rate,
         mortgage_rate, loan_program text 'FHA', created_at, updated_at)
listings(id, source text, source_id text, address, city, zip, lat, lng, price, units, beds, baths, sqft,
         year_built, listed_at, first_seen, last_seen, raw jsonb, UNIQUE(source, source_id))
parcel_facts(parcel_id pk, address, assessed_value, just_value, taxes_annual, millage, year_built,
             units, land_use_code, imported_at)
listing_parcel(listing_id, parcel_id)   -- resolved by address match
rent_estimates(listing_id, unit_no, est_rent, low, high, fetched_at)
analyses(id, listing_id, profile_id, run_at, net_monthly_cost, cash_to_close, lender_pass bool,
         self_sufficiency_ratio, drive_minutes, breakdown jsonb, program_results jsonb)
alert_events(id, profile_id, listing_id, kind, created_at, delivered_at)
program_rules(program, year, county, params jsonb)   -- FHA limits, HTH income limits, etc.
api_budget(source, month, calls_used)                -- RentCast call accounting
```

### Analyzer math (v1)
Inputs per listing: price, units, rent estimate per unit (or one estimate × units if per-unit missing, flagged), parcel taxes, profile.

Buyer view:
```
loan        = price × (1 − 0.035)  + upfront MIP 1.75% financed
P&I         = amortize(loan, mortgage_rate, 360)
annual MIP  = loan × 0.55% / 12
taxes       = parcel_taxes adjusted: owner unit share gets homestead ($50k off assessed, 3% cap);
              remaining units at full non-homestead rate     [see research notes]
insurance   = price × insurance_rate (profile default 1.2%/yr beachside, param)
reserves    = other_units_rent × (vacancy_rate + maintenance_rate)
other_units_rent = Σ rent_estimate for units ≠ owner's (owner takes smallest unit by default)
net_monthly_cost = P&I + MIP + taxes/12 + insurance/12 + reserves − other_units_rent
cash_to_close    = 3.5% down + closing (3% param) − Σ eligible program assistance
```
Lender view:
```
qualifying_rent  = 0.75 × other_units_rent
PITI             = P&I + MIP + taxes/12 + insurance/12
DTI check        = (PITI − qualifying_rent + other debts) / monthly income ≤ 0.43 (param)
self-sufficiency (units ≥ 3): 0.75 × Σ all units rent ≥ PITI   → pass/fail + ratio
FHA loan limit for county/units from program_rules
```
Program rules read `profile` + `program_rules(year)`: HTH (first-time, occupation flag OR veteran, income ≤ limit, 640+), Brevard HFA (first-time; limits TBD), SHIP (income ≤ 80% AMI, assets ≤ $15k), homestead (owner-occupied → yes).

Drive time: haversine distance × 1.4 road factor ÷ 30 mph default (param) in v1. Marked as estimate in UI. Real routing is a later swap behind `DriveTimeEstimator`.

### Web app
- `GET /` → ranked report for the (single) profile: rank, address, price, units, net monthly cost, cash-to-close, lender pass badge, drive minutes.
- `GET /listings/{id}` → full breakdown: buyer view table, lender view table, program results, raw listing facts, data freshness.
- `GET/POST /profile` → form; POST validates and saves; re-analysis happens on the analyzer's next poll (profile `updated_at` > analysis `run_at` ⇒ stale).
- `GET /api/profile`, `PUT /api/profile`, `GET /api/listings?sort=net_monthly_cost`, `GET /api/listings/{id}` → JSON (REST rubric item).
- `GET /health` on all three apps → `{status, db: ok, lastCollectorRun, lastAnalyzerRun}`.

### Collector budget rule
One listings search per day for Brevard 2–4 units (~30 calls/month). Rent estimates only for listings never estimated, oldest first, until `api_budget` reaches 45 for the month. Never exceed 50. Budget table updated in the same transaction as the fetch result.

### Testing
- Unit: `FinancingCalculator`, `ProgramRuleEngine`, `ListingScorer`, drive-time, budget accounting. Golden-number tests built from the researched examples (e.g. $400k duplex example from research notes).
- Integration: Ktor `testApplication` against a real local Postgres (`TEST_DATABASE_URL`), Flyway-migrated per test class.
- Doubles: `FakeRentCastClient` (canned JSON), `FakeClock`, `RecordingAlertSender`.
- CI: GitHub Actions with a Postgres service container.

### Rubric → work order
1. C-level: schema + migrations; profile form; fake-fed collector; analyzer math; report page; REST JSON; deploy to Render with real RentCast key.
2. B-level: integration tests, fakes (already), GitHub Actions CI, `/health` + JSON logs + request timing.
3. A-level: `alert_events` → email sender worker; Render auto-deploy on green main.

## Resolved after builder check (2026-10-21)

**Ryan's decisions**
- **Hosting:** Render free tier. In production, one web service runs the Ktor app *and* both schedulers (`RUN_WORKERS=collector,analyzer` env var starts them in-process). Locally, in tests, and in docker-compose the three processes stay separate; the same jars are built either way. Free Render Postgres (expires after 30 days; recreate and re-run Flyway + seed). Upgrade path: deploy the two worker jars as paid background workers and set `RUN_WORKERS=` on the web service.
- **Repo:** public GitHub repo `house-hack` under Ryan's account. The repo root is this folder (currently `kotlin-ktor-starter/`); Dockerfile, Procfile, and `.github/workflows` stay at root. Course docs move into the repo under `docs/course/` so cloud sessions and CI see them.

**Builder's calls (Claude; flagged for Ryan to veto)**
1. **Rent estimates:** one RentCast rent call per listing (the AVM returns a single-unit figure for Multi-Family); stored as `rent_estimates(listing_id, est_rent_per_unit, low, high, beds_assumed, fetched_at)` — no per-unit rows. `other_units_rent = est_rent_per_unit × (units − 1)`. Listings with no estimate or no parcel match are **excluded from the ranking** and shown in a "pending data (n)" footer; they are re-analyzed automatically when inputs arrive. Work queue = listings where `last_analyzed_at IS NULL OR last_analyzed_at < max(listing.last_seen, rent.fetched_at, parcel.imported_at, profile.updated_at)` (add `last_analyzed_at` to `listings`).
2. **Property tax basis:** recomputed at purchase price, not the seller's bill (Save Our Homes resets on sale). `owner_share = 1 / units`. `taxable = max(price × owner_share − 50_000, 0) + price × (1 − owner_share)`; `taxes = taxable × millage` with millage from the parcel's taxing district (fallback: county average, param). Seller's bill is shown for reference only.
3. **Program stacking:** `program_rules` carries an `exclusive_group`. HTH and Brevard HFA are both first-mortgage DPA → group `first_mortgage_dpa`, engine takes the single best (highest assistance). SHIP stacks. HTH evaluated under both TBA (borrower income) and Bond (household income) tests; `program_results` lists each with pass/fail and the engine uses the best passing flavor.
4. **BCPAO import scope:** stream-parse the full 225 MB download daily (the big-data component) but **persist only** parcels whose DOR land-use code is multi-family residential (2–9 units) plus any parcel matching an active listing address. Expected tens of thousands of rows, not hundreds of thousands.
5. **Profile columns added:** `other_monthly_debts`, `insurance_rate` (default 0.012), `closing_cost_rate` (0.03), `road_factor` (1.4), `avg_speed_mph` (30). Columns with defaults, not a settings table.
6. HikariCP + plain JDBC + Flyway rather than Exposed/jOOQ: fewer moving parts, matches course style.
7. Owner occupies one unit; v1 assumes equal units (`owner_share = 1/units`). Smallest-unit logic deferred until per-unit data exists.
8. Haversine × road factor ÷ avg speed for drive time in v1, labeled as estimate.
9. Single profile in v1; schema supports many.

## Left to the builder
- Exact RentCast endpoint parameters and response mapping (read developers.rentcast.io/llms.txt first).
- BCPAO WebData.zip internal format (inspect a download before designing the parser).
- Freemarker layout/styling.
