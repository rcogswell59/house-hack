# HouseHack C-Level Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the seven C-level rubric items: a Ktor web app with profile form and ranked report, a collector that pulls RentCast listings and BCPAO parcels daily, an analyzer that computes buyer-view and lender-view numbers, Postgres persistence, JSON REST endpoints, unit tests, and a Render deployment.

**Architecture:** Three Gradle applications (web, collector, analyzer) share one Postgres via small component modules. Collector and analyzer never call each other; the analyzer's work queue is "listings whose inputs changed since last analysis." In Render production the web process also starts both schedulers (`RUN_WORKERS`), locally they run as separate jars.

**Tech Stack:** Kotlin 1.9.22, Ktor 2.3.9 (server + client), Gradle 8.7, JDK 21, PostgreSQL 16, HikariCP 5.1.0, Flyway 10.10.0, kotlinx-serialization-json 1.6.3, kotlin-test-junit (JUnit 4, already in starter), Freemarker.

**Spec:** `docs/superpowers/specs/2026-10-21-house-hack-design.md` (read it first; this plan argues from it).

## Global Constraints

- JDK 21 only (`org.gradle.java.home` or `JAVA_HOME` must point at Temurin 21; Gradle 8.7 cannot run on 25).
- Kotlin 1.9.22 / Ktor 2.3.9 / Gradle 8.7 as in the starter. Do not bump.
- Every external call goes through an interface with a Fake. No test touches RentCast or bcpao.us.
- RentCast budget: never exceed 50 calls per calendar month; `api_budget` updated in the same transaction as the fetch result.
- Buyer view and lender view are separate fields; no code path combines them.
- Secrets only from env: `DATABASE_URL`, `RENTCAST_API_KEY`. Tests use `TEST_DATABASE_URL` (default `jdbc:postgresql://localhost:5432/househack_test?user=postgres&password=postgres`).
- Money in `Long` cents? No: use `Double` dollars throughout (course project; precision to the cent is not required). Round only for display.
- Package root `io.initialcapacity.househack`. Tests under `test.initialcapacity.househack`.
- Commit after every task with the attribution line `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## Review Focus

1. A listing with `units = 1` or `units > 4` must never be analyzed or ranked (FHA rule set is 2–4 units) — Task 7 test `analyze_skips_listings_outside_2_to_4_units`.
2. A listing whose RentCast rent estimate is missing must be excluded from the ranking (not ranked at $0 rent) — Task 7 test `analyze_returns_null_when_rent_estimate_missing`, Task 10 test `report_lists_pending_count`.
3. Collector runs on a day when the month's budget is at 50 must do nothing and log, not throw — Task 8 test `collect_skips_when_budget_exhausted`.
4. Profile form POST with a negative income, savings, or a credit score outside 300–850 must re-render the form with errors and not save — Task 10 test `profile_post_rejects_invalid_values`.
5. Render's `DATABASE_URL` arrives as `postgres://user:pass@host:5432/db`, not JDBC — Task 1 test `jdbcUrl_converts_postgres_scheme`.

---

## File structure

```
settings.gradle.kts                          (modify: add modules)
support/database-support/                    DataSource factory, Flyway runner, DatabaseTemplate, test helper
  src/main/resources/db/migration/V1__schema.sql, V2__program_rules_2026.sql
components/profile/                          BuyerProfile, ProfileValidator, ProfileRepository
components/listings-data/                    Listing, ParcelFact, RentEstimate, Analysis + repositories, ApiBudgetRepository
components/rentcast-client/                  RentCastClient, HttpRentCastClient, FakeRentCastClient, DTOs
components/bcpao-import/                     BcpaoDownloader, BcpaoParcelParser, ImportParcelsWorker
components/data-collector/                   CollectListingsWorker, FetchRentEstimatesWorker, DailyWorkFinder (replace Example*)
components/data-analyzer/                    FinancingCalculator, ProgramRuleEngine, DriveTimeEstimator, ListingAnalyzer, AnalyzeListingWorker, StaleAnalysisWorkFinder (replace Example*)
applications/basic-server/                   App.kt (routes), ProfileRoutes.kt, ReportRoutes.kt, ApiRoutes.kt, HealthRoute.kt, templates/*.ftl
applications/data-collector-server/App.kt    wire-up
applications/data-analyzer-server/App.kt     wire-up
render.yaml, docker-compose.yaml (modify), .github/workflows/ci.yml (replace deploy-review.yml), README.md
```

---

### Task 0: Toolchain green on JDK 21

**Files:**
- Modify: `gradle.properties` (add `org.gradle.java.home` comment only), `.github/workflows/deploy-review.yml` → rename to `ci.yml`
- Create: `.gitattributes`

- [ ] **Step 1: Point Gradle at JDK 21 for this shell and build the untouched starter**

Run (PowerShell): `$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Where-Object Name -like "jdk-21*" | Select-Object -First 1).FullName; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat clean build`
Expected: `BUILD SUCCESSFUL`, three jars under `applications/*/build/libs/`.

- [ ] **Step 2: Replace the CI workflow**

`.github/workflows/ci.yml`: trigger on `push` and `pull_request`; one job `test` on `ubuntu-latest` with a `postgres:16` service (`POSTGRES_PASSWORD=postgres`, `POSTGRES_DB=househack_test`, health check `pg_isready`), `actions/setup-java@v4` temurin 21 with gradle cache, step `./gradlew build` with env `TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/househack_test?user=postgres&password=postgres`. Delete `deploy-review.yml` (its artifact-registry job targets a cloud account we don't have).

- [ ] **Step 3: Add `.gitattributes`** with `* text=auto eol=lf` and `*.bat text eol=crlf`.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "ci: test-only workflow with Postgres service; normalize line endings"
```

---

### Task 1: database-support

**Files:**
- Create: `support/database-support/build.gradle.kts`, `src/main/kotlin/io/initialcapacity/househack/db/DatabaseConfiguration.kt`, `.../db/DatabaseTemplate.kt`, `.../db/Migrations.kt`, `src/main/resources/db/migration/V1__schema.sql`, `src/testFixtures/kotlin/test/initialcapacity/househack/db/TestDatabase.kt`
- Modify: `settings.gradle.kts` (include `support:database-support`), root `build.gradle.kts` (apply `java-test-fixtures` plugin to subprojects)
- Test: `support/database-support/src/test/kotlin/test/initialcapacity/househack/db/DatabaseConfigurationTest.kt`, `MigrationsTest.kt`

**Interfaces:**
- Produces: `class DatabaseConfiguration(val jdbcUrl: String) { val dataSource: javax.sql.DataSource }`; `companion fun fromEnv(name: String = "DATABASE_URL"): DatabaseConfiguration`; `fun jdbcUrl(raw: String): String` (pure, converts `postgres://u:p@h:port/db` → `jdbc:postgresql://h:port/db?user=u&password=p`, passes `jdbc:` through).
- `object Migrations { fun run(dataSource: DataSource) }` (Flyway, locations `classpath:db/migration`).
- `class DatabaseTemplate(private val dataSource: DataSource) { fun <T> query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): List<T>; fun <T> queryOne(sql, params, mapper): T?; fun update(sql: String, vararg params: Any?): Int; fun <T> inTransaction(block: (Connection) -> T): T }`. Params bound by position with `setObject`; `java.time.Instant` bound as `Timestamp`.
- Test fixture: `object TestDatabase { val dataSource: DataSource /* from TEST_DATABASE_URL, migrated once per JVM */; fun truncateAll() }`.

- [ ] **Step 1: Write failing tests**

```kotlin
class DatabaseConfigurationTest {
    @Test fun jdbcUrl_converts_postgres_scheme() =
        assertEquals("jdbc:postgresql://h.render.com:5432/db?user=u&password=p",
            jdbcUrl("postgres://u:p@h.render.com:5432/db"))
    @Test fun jdbcUrl_passes_jdbc_through() =
        assertEquals("jdbc:postgresql://localhost/x", jdbcUrl("jdbc:postgresql://localhost/x"))
}
class MigrationsTest {
    @Test fun migrate_creates_all_tables() {
        Migrations.run(TestDatabase.dataSource)
        val tables = DatabaseTemplate(TestDatabase.dataSource).query(
            "select table_name from information_schema.tables where table_schema='public'") { it.getString(1) }
        assertTrue(tables.containsAll(listOf("profiles","listings","parcel_facts","rent_estimates","analyses","alert_events","program_rules","api_budget")))
    }
}
```

- [ ] **Step 2: Run, expect compile failure** — `./gradlew :support:database-support:test`

- [ ] **Step 3: Implement.** Dependencies: `com.zaxxer:HikariCP:5.1.0`, `org.postgresql:postgresql:42.7.3`, `org.flywaydb:flyway-core:10.10.0`, `org.flywaydb:flyway-database-postgresql:10.10.0`. Hikari `maximumPoolSize = 5`.

`V1__schema.sql` (exact):

```sql
create table profiles (
  id bigserial primary key, name text not null,
  annual_income numeric(12,2) not null, liquid_savings numeric(12,2) not null, credit_score int not null,
  first_time_buyer boolean not null, hth_eligible_occupation boolean not null default false, veteran boolean not null default false,
  household_size int not null default 1, other_monthly_debts numeric(10,2) not null default 0,
  workplace_lat double precision not null, workplace_lng double precision not null, max_drive_minutes int not null,
  units_min int not null default 2, units_max int not null default 4,
  vacancy_rate numeric(5,4) not null default 0.08, maintenance_rate numeric(5,4) not null default 0.05,
  insurance_rate numeric(5,4) not null default 0.012, closing_cost_rate numeric(5,4) not null default 0.03,
  mortgage_rate numeric(6,5) not null default 0.065, loan_program text not null default 'FHA',
  road_factor numeric(4,2) not null default 1.4, avg_speed_mph numeric(5,2) not null default 30,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now());
create table listings (
  id bigserial primary key, source text not null, source_id text not null, address text not null,
  city text, zip text, lat double precision, lng double precision, price numeric(12,2) not null,
  bedrooms int, bathrooms numeric(4,1), sqft int, year_built int, property_type text,
  listed_at timestamptz, first_seen timestamptz not null default now(), last_seen timestamptz not null default now(),
  active boolean not null default true, last_analyzed_at timestamptz, raw jsonb not null, unique(source, source_id));
create table parcel_facts (
  parcel_id text primary key, address_norm text not null, city text, zip text, just_value numeric(12,2),
  assessed_value numeric(12,2), taxes_annual numeric(10,2), millage numeric(8,4), year_built int,
  units int, land_use_code text, imported_at timestamptz not null default now());
create index parcel_facts_address_idx on parcel_facts(address_norm);
create table listing_parcel (listing_id bigint primary key references listings(id), parcel_id text not null references parcel_facts(parcel_id));
create table rent_estimates (
  listing_id bigint primary key references listings(id), est_rent_per_unit numeric(10,2) not null,
  low numeric(10,2), high numeric(10,2), beds_assumed int, fetched_at timestamptz not null default now());
create table analyses (
  id bigserial primary key, listing_id bigint not null references listings(id), profile_id bigint not null references profiles(id),
  run_at timestamptz not null default now(), net_monthly_cost numeric(10,2) not null, cash_to_close numeric(12,2) not null,
  lender_pass boolean not null, self_sufficiency_ratio numeric(6,3), dti numeric(5,3) not null, drive_minutes numeric(6,1) not null,
  breakdown jsonb not null, program_results jsonb not null, unique(listing_id, profile_id));
create table alert_events (id bigserial primary key, profile_id bigint not null, listing_id bigint not null, kind text not null,
  created_at timestamptz not null default now(), delivered_at timestamptz);
create table program_rules (program text not null, year int not null, county text not null, params jsonb not null, primary key(program, year, county));
create table api_budget (source text not null, month date not null, calls_used int not null default 0, primary key(source, month));
```

`TestDatabase` reads `TEST_DATABASE_URL` (default from Global Constraints), runs `Migrations.run` once (lazy), `truncateAll()` truncates every table except `flyway_schema_history` and `program_rules` with `restart identity cascade`.

- [ ] **Step 4: Run tests, expect PASS.** Requires local Postgres with database `househack_test` (`createdb -U postgres househack_test`).

- [ ] **Step 5: Commit** — `feat(db): datasource, flyway V1 schema, test database fixture`

---

### Task 2: profile component

**Files:**
- Create: `components/profile/build.gradle.kts`, `src/main/kotlin/io/initialcapacity/househack/profile/BuyerProfile.kt`, `ProfileValidator.kt`, `ProfileRepository.kt`
- Test: `components/profile/src/test/kotlin/test/initialcapacity/househack/profile/ProfileValidatorTest.kt`, `ProfileRepositoryTest.kt`

**Interfaces:**
- Produces: `data class BuyerProfile(val id: Long? = null, val name: String, val annualIncome: Double, val liquidSavings: Double, val creditScore: Int, val firstTimeBuyer: Boolean, val hthEligibleOccupation: Boolean, val veteran: Boolean, val householdSize: Int, val otherMonthlyDebts: Double, val workplaceLat: Double, val workplaceLng: Double, val maxDriveMinutes: Int, val unitsMin: Int, val unitsMax: Int, val vacancyRate: Double, val maintenanceRate: Double, val insuranceRate: Double, val closingCostRate: Double, val mortgageRate: Double, val loanProgram: String = "FHA", val roadFactor: Double, val avgSpeedMph: Double, val updatedAt: Instant? = null)` with `companion fun defaults(): BuyerProfile` (Ryan's seed: name "Ryan", income 118000.0, savings 20000.0, credit 720, firstTime true, hth false, veteran false, household 1, debts 0.0, workplace 28.3922/-80.6077, 30 min, 2–4 units, rates per V1 defaults).
- `object ProfileValidator { fun validate(p: BuyerProfile): List<String> }` — errors for: income ≤ 0, savings < 0, credit outside 300..850, maxDriveMinutes not in 1..180, unitsMin < 2 or unitsMax > 4 or unitsMin > unitsMax, any rate outside 0.0..1.0, mortgageRate outside 0.0..0.25.
- `class ProfileRepository(db: DatabaseTemplate) { fun save(p: BuyerProfile): BuyerProfile /* insert or update by id, sets updated_at = now() */; fun findFirst(): BuyerProfile? }`.

- [ ] **Step 1: Failing tests** — `ProfileValidatorTest`: `defaults_are_valid` (empty list), `rejects_negative_income`, `rejects_credit_out_of_range` (299 and 851), `rejects_units_min_above_max`. `ProfileRepositoryTest` (uses `TestDatabase.truncateAll()` in `@Before`): `save_then_findFirst_roundtrips_every_field`, `save_with_id_updates_and_bumps_updated_at`.
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement.** Module depends on `:support:database-support` (+ `testFixtures(project(":support:database-support"))`).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `feat(profile): buyer profile model, validation, repository`

---

### Task 3: listings-data

**Files:**
- Create: `components/listings-data/build.gradle.kts`, `src/main/kotlin/io/initialcapacity/househack/listings/{Listing.kt, ListingRepository.kt, ParcelFact.kt, ParcelFactRepository.kt, RentEstimate.kt, RentEstimateRepository.kt, Analysis.kt, AnalysisRepository.kt, ApiBudgetRepository.kt, AddressNormalizer.kt}`
- Test: `components/listings-data/src/test/kotlin/test/initialcapacity/househack/listings/*Test.kt`

**Interfaces (produces):**
- `data class Listing(id: Long?, source: String, sourceId: String, address: String, city: String?, zip: String?, lat: Double?, lng: Double?, price: Double, bedrooms: Int?, bathrooms: Double?, sqft: Int?, yearBuilt: Int?, propertyType: String?, listedAt: Instant?, firstSeen: Instant?, lastSeen: Instant?, active: Boolean = true, lastAnalyzedAt: Instant?, raw: String /* json */)`
- `class ListingRepository(db) { fun upsertSeen(l: Listing, seenAt: Instant): Listing /* on conflict (source,source_id) update price, last_seen, active=true, raw */; fun markInactiveNotSeenSince(source: String, cutoff: Instant): Int; fun findById(id: Long): Listing?; fun findActive(): List<Listing>; fun findWithoutRentEstimate(limit: Int): List<Listing>; fun findStaleForAnalysis(profileUpdatedAt: Instant): List<Listing> /* active and (last_analyzed_at is null or < greatest(last_seen, rent.fetched_at, parcel.imported_at, :profileUpdatedAt)) */; fun markAnalyzed(id: Long, at: Instant) }`
- `data class ParcelFact(parcelId: String, addressNorm: String, city: String?, zip: String?, justValue: Double?, assessedValue: Double?, taxesAnnual: Double?, millage: Double?, yearBuilt: Int?, units: Int?, landUseCode: String?, importedAt: Instant?)`
- `class ParcelFactRepository(db) { fun upsertAll(facts: List<ParcelFact>): Int; fun findByAddressNorm(a: String): ParcelFact?; fun linkListing(listingId: Long, parcelId: String); fun findForListing(listingId: Long): ParcelFact? }`
- `object AddressNormalizer { fun normalize(street: String): String }` — uppercase, strip punctuation, collapse spaces, abbreviate STREET→ST, AVENUE→AVE, ROAD→RD, DRIVE→DR, BOULEVARD→BLVD, COURT→CT, LANE→LN, NORTH/SOUTH/EAST/WEST→N/S/E/W; drops anything after a comma.
- `data class RentEstimate(listingId: Long, estRentPerUnit: Double, low: Double?, high: Double?, bedsAssumed: Int?, fetchedAt: Instant?)`; `class RentEstimateRepository(db) { fun save(r: RentEstimate); fun findForListing(listingId: Long): RentEstimate? }`
- `data class Analysis(id: Long?, listingId: Long, profileId: Long, runAt: Instant?, netMonthlyCost: Double, cashToClose: Double, lenderPass: Boolean, selfSufficiencyRatio: Double?, dti: Double, driveMinutes: Double, breakdownJson: String, programResultsJson: String)`; `class AnalysisRepository(db) { fun upsert(a: Analysis): Analysis; fun findForListing(listingId: Long, profileId: Long): Analysis?; fun rankedForProfile(profileId: Long): List<Pair<Analysis, Listing>> /* active listings, order by net_monthly_cost asc */; fun countPendingForProfile(profileId: Long): Int /* active listings with no analysis row */ }`
- `class ApiBudgetRepository(db) { fun callsUsed(source: String, month: LocalDate): Int; fun tryReserve(source: String, month: LocalDate, limit: Int, n: Int = 1): Boolean /* single statement: insert ... on conflict do update set calls_used = calls_used + n where calls_used + n <= limit; returns rowcount == 1 */ }`

- [ ] **Step 1: Failing tests** (one class per repository; `TestDatabase.truncateAll()` in `@Before`):
  - `ListingRepositoryTest`: `upsertSeen_inserts_then_updates_price_and_last_seen`, `markInactiveNotSeenSince_flags_old_rows`, `findStaleForAnalysis_returns_never_analyzed_and_profile_changed`, `findStaleForAnalysis_excludes_fresh`.
  - `AddressNormalizerTest`: `normalize("123 North Atlantic Avenue, Cape Canaveral") == "123 N ATLANTIC AVE"`.
  - `ParcelFactRepositoryTest`: `upsertAll_then_findByAddressNorm`, `linkListing_then_findForListing`.
  - `AnalysisRepositoryTest`: `upsert_replaces_for_same_listing_and_profile`, `rankedForProfile_orders_by_net_cost_asc`, `countPendingForProfile_counts_unanalyzed_active`.
  - `ApiBudgetRepositoryTest`: `tryReserve_allows_up_to_limit_then_refuses` (limit 3: three trues, fourth false; `callsUsed == 3`).
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement.** Depends on `:support:database-support`. JSON columns bound via `PGobject(type="jsonb")`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `feat(listings-data): listing, parcel, rent, analysis, budget repositories`

---

### Task 4: rentcast-client

**Files:**
- Create: `components/rentcast-client/build.gradle.kts`, `src/main/kotlin/io/initialcapacity/househack/rentcast/{RentCastClient.kt, HttpRentCastClient.kt, FakeRentCastClient.kt, RentCastDtos.kt}`, `src/main/resources/rentcast/sample-sale-listings.json`, `sample-rent-estimate.json`
- Test: `components/rentcast-client/src/test/kotlin/test/initialcapacity/househack/rentcast/HttpRentCastClientTest.kt`

**Interfaces (produces):**
- `data class SaleListingDto(id: String, formattedAddress: String, addressLine1: String?, city: String?, zipCode: String?, latitude: Double?, longitude: Double?, propertyType: String?, bedrooms: Int?, bathrooms: Double?, squareFootage: Int?, yearBuilt: Int?, price: Double, status: String?, listedDate: String?, raw: String)` (`@Serializable` with `ignoreUnknownKeys`; `raw` filled from the element's own JSON text).
- `data class RentEstimateDto(rent: Double, rentRangeLow: Double?, rentRangeHigh: Double?)`
- `interface RentCastClient { suspend fun searchSaleListings(lat: Double, lng: Double, radiusMiles: Double, propertyType: String = "Multi-Family", limit: Int = 500): List<SaleListingDto>; suspend fun rentEstimate(address: String, propertyType: String = "Multi-Family", bedrooms: Int?, bathrooms: Double?, sqft: Int?): RentEstimateDto }`
- `class HttpRentCastClient(apiKey: String, engine: HttpClientEngine = CIO.create(), baseUrl: String = "https://api.rentcast.io/v1") : RentCastClient` — GET `/listings/sale` with `latitude, longitude, radius, propertyType, status=Active, limit`; GET `/avm/rent/long-term` with `address, propertyType, bedrooms, bathrooms, squareFootage` (omit nulls); header `X-Api-Key`. Non-2xx → `RentCastException(status, body)`.
- `class FakeRentCastClient(var listings: List<SaleListingDto> = sampleListings(), var rent: RentEstimateDto = RentEstimateDto(1800.0, 1700.0, 1900.0)) : RentCastClient` records `calls: MutableList<String>`; `companion fun sampleListings(): List<SaleListingDto>` parses the bundled sample JSON (hand-write 6 Brevard-area Multi-Family listings in `sample-sale-listings.json`, matching the real schema fields; include one with `bedrooms = null`).

- [ ] **Step 1: Failing tests** using Ktor `MockEngine`: `searchSaleListings_sends_key_and_params_and_parses` (asserts request path `/v1/listings/sale`, query `propertyType=Multi-Family`, `status=Active`, header `X-Api-Key=k`, 2 listings parsed, first `price == 425000.0`), `rentEstimate_parses_rent_and_range`, `non_2xx_throws_RentCastException`.
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement.** Deps: `io.ktor:ktor-client-core-jvm`, `ktor-client-cio-jvm`, `ktor-client-content-negotiation-jvm`, `ktor-serialization-kotlinx-json-jvm` (all `$ktorVersion`), `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3`; test `io.ktor:ktor-client-mock-jvm`. Apply the serialization plugin.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `feat(rentcast): http client with fake and samples`

---

### Task 5: FinancingCalculator (pure)

**Files:**
- Create: `components/data-analyzer/src/main/kotlin/io/initialcapacity/househack/analyzer/{FinancingInputs.kt, FinancingCalculator.kt}`
- Delete: `components/data-analyzer/src/main/kotlin/io/initialcapacity/analyzer/Example*.kt` (and the collector's `Example*.kt` in Task 8)
- Test: `components/data-analyzer/src/test/kotlin/test/initialcapacity/househack/analyzer/FinancingCalculatorTest.kt`

**Interfaces (produces):**
- `data class FinancingInputs(price: Double, units: Int, rentPerUnit: Double, millage: Double /* e.g. 0.016 */, mortgageRate: Double, insuranceRate: Double, vacancyRate: Double, maintenanceRate: Double, closingCostRate: Double, monthlyIncome: Double, otherMonthlyDebts: Double, assistanceTotal: Double, downPaymentRate: Double = 0.035, upfrontMipRate: Double = 0.0175, annualMipRate: Double = 0.0055, homesteadExemption: Double = 50_000.0, maxDti: Double = 0.43)`
- `data class BuyerView(loanAmount: Double, principalAndInterest: Double, monthlyMip: Double, monthlyTaxes: Double, monthlyInsurance: Double, reserves: Double, otherUnitsRent: Double, netMonthlyCost: Double, cashToClose: Double)`
- `data class LenderView(piti: Double, qualifyingRent: Double, dti: Double, dtiPass: Boolean, selfSufficiencyRatio: Double? /* null for 2 units */, selfSufficiencyPass: Boolean? , pass: Boolean /* dtiPass && (ss == null || ss) */)`
- `object FinancingCalculator { fun amortizedPayment(principal: Double, annualRate: Double, months: Int = 360): Double; fun annualTaxes(price: Double, units: Int, millage: Double, homesteadExemption: Double): Double; fun buyerView(i: FinancingInputs): BuyerView; fun lenderView(i: FinancingInputs): LenderView }`

- [ ] **Step 1: Failing tests** (golden duplex: price 400 000, 2 units, rent 1 800, millage 0.016, rate 0.065, insurance 0.012, vacancy 0.08, maintenance 0.05, closing 0.03, income 118 000/12, debts 0, assistance 15 000):

```kotlin
@Test fun amortized_payment_matches_standard_formula() =
    assertEquals(2482.5, FinancingCalculator.amortizedPayment(392_755.0, 0.065), 1.0)
@Test fun annual_taxes_apply_homestead_to_owner_share_only() =
    assertEquals(5_600.0, FinancingCalculator.annualTaxes(400_000.0, 2, 0.016, 50_000.0), 0.01)  // (200k-50k + 200k) * 0.016
@Test fun buyer_view_golden_duplex() { val v = FinancingCalculator.buyerView(golden)
    assertEquals(392_755.0, v.loanAmount, 0.01)          // 386,000 base + 1.75% UFMIP financed
    assertEquals(180.01, v.monthlyMip, 0.05)
    assertEquals(466.67, v.monthlyTaxes, 0.05)
    assertEquals(400.0, v.monthlyInsurance, 0.01)
    assertEquals(234.0, v.reserves, 0.01)                 // 1800 * (0.08 + 0.05)
    assertEquals(1800.0, v.otherUnitsRent, 0.01)
    assertEquals(1963.2, v.netMonthlyCost, 1.5)
    assertEquals(11_000.0, v.cashToClose, 0.01) }         // 14,000 down + 12,000 closing − 15,000
@Test fun lender_view_golden_duplex_passes_dti_and_has_no_self_sufficiency() { val l = FinancingCalculator.lenderView(golden)
    assertEquals(1350.0, l.qualifyingRent, 0.01); assertEquals(0.222, l.dti, 0.002); assertNull(l.selfSufficiencyRatio); assertTrue(l.pass) }
@Test fun lender_view_triplex_self_sufficiency_ratio() { val l = FinancingCalculator.lenderView(golden.copy(price = 500_000.0, units = 3, rentPerUnit = 1_500.0))
    assertEquals(0.75 * 4_500.0 / l.piti, l.selfSufficiencyRatio!!, 0.001); assertEquals(l.selfSufficiencyRatio!! >= 1.0, l.selfSufficiencyPass) }
```

- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement** per spec formulas: `loan = price × (1 − down) × (1 + upfrontMip)`; `otherUnitsRent = rentPerUnit × (units − 1)`; `netMonthlyCost = P&I + MIP + taxes/12 + insurance/12 + reserves − otherUnitsRent`; `cashToClose = price × down + price × closing − assistance`; `qualifyingRent = 0.75 × otherUnitsRent`; `dti = (piti − qualifyingRent + otherDebts) / monthlyIncome`; self-sufficiency only when `units >= 3`: `0.75 × rentPerUnit × units / piti`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `feat(analyzer): financing calculator with buyer and lender views`

---

### Task 6: ProgramRuleEngine + 2026 rules seed

**Files:**
- Create: `support/database-support/src/main/resources/db/migration/V2__program_rules_2026.sql`, `components/data-analyzer/src/main/kotlin/io/initialcapacity/househack/analyzer/{ProgramRules.kt, ProgramRuleEngine.kt}`
- Test: `.../analyzer/ProgramRuleEngineTest.kt`

**Interfaces (produces):**
- `data class ProgramRule(program: String, year: Int, county: String, params: JsonObject)`; `class ProgramRuleRepository(db) { fun forYear(year: Int, county: String): List<ProgramRule> }` (lives in listings-data? No — in data-analyzer, which depends on database-support).
- `data class ProgramResult(program: String, eligible: Boolean, assistance: Double, reasons: List<String>, exclusiveGroup: String?)`
- `data class ProgramOutcome(results: List<ProgramResult>, totalAssistance: Double /* best of each exclusive group + all non-exclusive eligible */)`
- `class ProgramRuleEngine(rules: List<ProgramRule>) { fun evaluate(profile: BuyerProfile, price: Double, units: Int, loanAmount: Double): ProgramOutcome }`

`V2` seeds `(program, 2026, 'Brevard', params)` rows:
- `FHA_LIMITS`: `{"1":541287,"2":693050,"3":837700,"4":1041125}`
- `HTH`: `{"exclusive_group":"first_mortgage_dpa","pct_of_loan":0.05,"min":10000,"max":35000,"min_credit":640,"income_limit_tba":149400,"income_limit_bond_1_2":101414,"income_limit_bond_3plus":116627,"requires_first_time_unless_veteran":true}`
- `BREVARD_HFA`: `{"exclusive_group":"first_mortgage_dpa","amount":15000,"requires_first_time":true}`
- `SHIP`: `{"exclusive_group":null,"max_very_low":75000,"max_low":60000,"ami_80_by_household":{"1":56500,"2":64600,"3":72650,"4":80700},"ami_50_by_household":{"1":35300,"2":40350,"3":45400,"4":50450},"max_liquid_assets":15000,"source_note":"AMI figures are estimates — verify against HUD 2026 Brevard limits"}`
- `HOMESTEAD`: `{"exemption":50000}`

- [ ] **Step 1: Failing tests** (build `ProgramRule`s inline from the same JSON as V2; `ryan = BuyerProfile.defaults()`):
  - `ryan_is_ineligible_for_hth_without_occupation_or_veteran` (reasons contains "occupation")
  - `veteran_with_income_under_limit_gets_hth_5pct_clamped` (profile veteran=true, loan 392 755 → assistance 19 637.75)
  - `hth_assistance_clamps_to_min_and_max` (loan 150 000 → 10 000; loan 900 000 → 35 000)
  - `ryan_gets_brevard_hfa_15000`
  - `ryan_fails_ship_on_income`
  - `exclusive_group_takes_best_single_program` (veteran profile: HTH 19 637.75 beats HFA 15 000; total = 19 637.75, not the sum)
  - `price_above_fha_limit_marks_fha_ineligible` (2 units, price 750 000 → result program `FHA_LIMITS` eligible=false)
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement.** Engine is pure given `rules`; repository is a thin JDBC read.
- [ ] **Step 4: Run, expect PASS** (`MigrationsTest` still green with V2).
- [ ] **Step 5: Commit** — `feat(analyzer): program rule engine with 2026 Brevard seed`

---

### Task 7: DriveTimeEstimator, ListingAnalyzer, analyzer worker

**Files:**
- Create: `.../analyzer/{DriveTimeEstimator.kt, ListingAnalyzer.kt, AnalyzeListingTask.kt, StaleAnalysisWorkFinder.kt, AnalyzeListingWorker.kt}`
- Test: `DriveTimeEstimatorTest.kt`, `ListingAnalyzerTest.kt`, `AnalyzeListingWorkerTest.kt`

**Interfaces (produces):**
- `object DriveTimeEstimator { fun haversineMiles(lat1, lng1, lat2, lng2): Double; fun minutes(fromLat, fromLng, toLat, toLng, roadFactor: Double, avgSpeedMph: Double): Double }`
- `class ListingAnalyzer(engine: ProgramRuleEngine) { fun analyze(profile: BuyerProfile, listing: Listing, parcel: ParcelFact?, rent: RentEstimate?): Analysis? }` — returns `null` (with a logged reason) when `rent == null`, when `units` (parcel.units ?: `ceil(bedrooms/2.0)` if bedrooms != null, else null) is null or outside `profile.unitsMin..unitsMax`, or when listing lat/lng is null. Millage = `parcel.millage ?: 0.016`. `breakdownJson` serializes `BuyerView`, `LenderView`, and `{"unitsSource":"parcel"|"bedrooms_fallback","millageSource":"parcel"|"default"}`.
- `data class AnalyzeListingTask(listingId: Long, profileId: Long)`; `class StaleAnalysisWorkFinder(listings: ListingRepository, profiles: ProfileRepository) : WorkFinder<AnalyzeListingTask>`; `class AnalyzeListingWorker(analyzer, listings, parcels, rents, profiles, analyses) : Worker<AnalyzeListingTask>` — on `analyze == null` still calls `listings.markAnalyzed` so the row leaves the queue (it re-enters when inputs change); on success `analyses.upsert` then `markAnalyzed`.

- [ ] **Step 1: Failing tests**
  - `DriveTimeEstimatorTest.cocoa_to_cape_canaveral_is_about_23_minutes`: `minutes(28.3861, -80.7420, 28.3922, -80.6077, 1.4, 30.0)` within 0.5 of 22.9.
  - `ListingAnalyzerTest`: `analyze_golden_duplex_matches_calculator` (listing price 400 000, bedrooms 4, parcel units 2 millage 0.016, rent 1 800 → `netMonthlyCost` within 1.5 of 1963.2, `cashToClose == 11_000.0`, `lenderPass`), `analyze_returns_null_when_rent_estimate_missing`, `analyze_skips_listings_outside_2_to_4_units` (parcel units 1 and 5 → null), `analyze_uses_bedroom_fallback_when_no_parcel` (bedrooms 4, parcel null → units 2, breakdown `unitsSource == "bedrooms_fallback"`), `analyze_excludes_beyond_max_drive` (listing in Orlando 28.5383,-81.3792 → null).
  - `AnalyzeListingWorkerTest` (real DB): `worker_writes_analysis_and_marks_analyzed`, `finder_returns_stale_listings_only`.
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement.** `data-analyzer` depends on `:components:listings-data`, `:components:profile`, `:support:workflow-support`, kotlinx-serialization-json.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `feat(analyzer): listing analyzer and stale-analysis worker`

---

### Task 8: Collector workers (listings + rent estimates with budget)

**Files:**
- Create: `components/data-collector/src/main/kotlin/io/initialcapacity/househack/collector/{CollectorTask.kt, DailyWorkFinder.kt, CollectListingsWorker.kt, FetchRentEstimatesWorker.kt}`
- Delete: `components/data-collector/src/main/kotlin/io/initialcapacity/collector/Example*.kt`
- Test: `CollectListingsWorkerTest.kt`, `FetchRentEstimatesWorkerTest.kt`, `DailyWorkFinderTest.kt`

**Interfaces (produces):**
- `data class CollectorTask(name: String, runDate: LocalDate)`
- `class DailyWorkFinder(db: DatabaseTemplate, clock: Clock) : WorkFinder<CollectorTask>` — table-free: keeps an in-memory `lastRun: MutableMap<String, LocalDate>`; `findRequested(name)` returns one task if `lastRun[name] != today`, `markCompleted` records it. (Process restart re-runs once; acceptable and idempotent because of upserts and budget.)
- `class CollectListingsWorker(client: RentCastClient, listings: ListingRepository, budget: ApiBudgetRepository, profiles: ProfileRepository, clock: Clock, radiusMiles: Double = 30.0, monthlyLimit: Int = 50) : Worker<CollectorTask>` (`name = "collect-listings"`) — profile → center; `budget.tryReserve("rentcast", firstOfMonth, monthlyLimit)` else log+return; `searchSaleListings`; `upsertSeen` each; `markInactiveNotSeenSince(source, now − 3 days)`.
- `class FetchRentEstimatesWorker(client, listings, rents, parcels, budget, clock, perRunCap: Int = 5, monthlyLimit: Int = 45) : Worker<CollectorTask>` (`name = "fetch-rents"`) — for each of `listings.findWithoutRentEstimate(perRunCap)`: `bedsAssumed = bedrooms?.let { ceil(it / (parcel?.units ?: 2).toDouble()).toInt() }`; reserve one call (limit 45 keeps 5 in reserve for the daily search); on success `rents.save`.

- [ ] **Step 1: Failing tests** (Fake client, real DB, fixed `Clock`):
  - `collect_upserts_all_listings_from_client_and_reserves_one_call` (6 sample listings → 6 rows active; `callsUsed == 1`)
  - `collect_marks_missing_listings_inactive` (second run with 5 listings → one `active=false`)
  - `collect_skips_when_budget_exhausted` (pre-seed `calls_used = 50`; `client.calls.isEmpty()`, no throw)
  - `fetch_rents_caps_per_run_and_counts_budget` (6 listings without rent, cap 5 → 5 saved, `callsUsed == 5`)
  - `fetch_rents_passes_beds_per_unit` (bedrooms 4, parcel units 2 → fake saw `bedrooms=2`)
  - `daily_finder_returns_one_task_per_day`
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement.** Dependencies: listings-data, profile, rentcast-client, workflow-support, coroutines (`runBlocking` around suspend client calls as the starter does).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `feat(collector): daily listings and budgeted rent-estimate workers`

---

### Task 9: bcpao-import

**Files:**
- Create: `components/bcpao-import/build.gradle.kts`, `src/main/kotlin/io/initialcapacity/househack/bcpao/{BcpaoDownloader.kt, BcpaoParcelParser.kt, ImportParcelsWorker.kt}`, `src/test/resources/bcpao/sample-parcels.<ext>`
- Test: `BcpaoParcelParserTest.kt`, `ImportParcelsWorkerTest.kt`

**Interfaces (produces):**
- `interface BcpaoDownloader { fun download(): java.nio.file.Path }`; `class HttpBcpaoDownloader(url: String = "https://www.bcpao.us/Docs/PublicDownload/PropData/WebData.zip", dir: Path)` streams to disk; `class FakeBcpaoDownloader(path: Path)`.
- `class BcpaoParcelParser(multiFamilyLandUseCodes: Set<String>) { fun parse(zip: Path, keepAddressNorms: Set<String>, sink: (ParcelFact) -> Unit) }` — streams; emits a fact when `landUseCode in multiFamilyLandUseCodes || addressNorm in keepAddressNorms`.
- `class ImportParcelsWorker(downloader, parser, parcels: ParcelFactRepository, listings: ListingRepository) : Worker<CollectorTask>` (`name = "import-parcels"`) — downloads, collects `keepAddressNorms` from active listings, parses, `upsertAll` in batches of 500, then links each active listing to a parcel by `addressNorm` when unlinked.

- [ ] **Step 1: Inspect the real file once** (not a test): download `WebData.zip` to the scratchpad, list its entries, identify the parcel table and the columns for parcel id, situs address, city, zip, just value, assessed value, total taxes, millage (or taxing district), year built, living units, DOR land-use code. Record column names and the file format (CSV / MDB / fixed-width) in `docs/course/research/bcpao-webdata-format.md`. **If the format is an Access MDB, stop and ask Ryan** (parsing MDB needs a library decision: Jackcess 4.x). Extract 20 rows including ≥3 multi-family parcels into `src/test/resources/bcpao/sample-parcels.<ext>` and zip it as the test fixture.
- [ ] **Step 2: Failing tests** — `parser_emits_only_multifamily_or_requested_addresses` (fixture: expect exactly the multi-family rows + one requested single-family address), `parser_maps_columns` (one known row's `units`, `millage`, `justValue`), `worker_upserts_and_links_active_listings` (real DB; listing with matching address gets `findForListing != null`).
- [ ] **Step 3: Run, expect failure.** Step 4: implement. Step 5: run, expect PASS.
- [ ] **Step 6: Commit** — `feat(bcpao): streaming parcel import filtered to multi-family and listed addresses`

---

### Task 10: Web application (pages + JSON API + health)

**Files:**
- Modify: `applications/basic-server/build.gradle.kts` (deps: profile, listings-data, data-analyzer, data-collector, bcpao-import, rentcast-client, database-support, `ktor-server-content-negotiation-jvm`, `ktor-serialization-kotlinx-json-jvm`, `ktor-server-status-pages-jvm`), `src/main/kotlin/io/initialcapacity/web/App.kt` → move to `io/initialcapacity/househack/web/App.kt`
- Create: `.../web/{Dependencies.kt, ProfileRoutes.kt, ReportRoutes.kt, ApiRoutes.kt, HealthRoute.kt, Workers.kt}`, templates `profile.ftl`, `report.ftl`, `listing.ftl`; replace `index.ftl`; keep `template.ftl` layout.
- Test: `applications/basic-server/src/test/kotlin/test/initialcapacity/househack/web/{ProfileRoutesTest, ReportRoutesTest, ApiRoutesTest, HealthRouteTest}.kt`

**Interfaces (produces):**
- `class Dependencies(dataSource: DataSource)` builds `DatabaseTemplate` and every repository; `fun Application.module(deps: Dependencies, startWorkers: Set<String> = emptySet(), rentCast: RentCastClient? = null)`.
- Routes: `GET /` → redirect to `/report`. `GET /profile` form prefilled from `findFirst() ?: defaults()`. `POST /profile` (form-urlencoded) → validate → save → 303 to `/report`; on errors 400 with the form re-rendered and `errors` list. `GET /report` → `rankedForProfile` rows (rank, address, city, price, units, netMonthlyCost, cashToClose, lenderPass, driveMinutes) plus `pendingCount` and `lastCollectorRun`. `GET /listings/{id}` → listing, parcel, rent, analysis breakdown tables. `GET /api/profile`, `PUT /api/profile` (JSON `BuyerProfile`, 400 with `{"errors":[...]}`), `GET /api/listings?sort=net_monthly_cost` → `[{listingId, address, price, units, netMonthlyCost, cashToClose, lenderPass, driveMinutes}]`, `GET /api/listings/{id}` → full `Analysis` + `Listing`. `GET /health` → `{"status":"ok","db":"ok","pendingListings":n,"activeListings":n}` (503 if DB ping fails).
- `object Workers { fun start(deps, names: Set<String>, rentCast: RentCastClient, clock: Clock): List<WorkScheduler<*>> }` — `"collector"` → `WorkScheduler(DailyWorkFinder, [CollectListingsWorker, ImportParcelsWorker, FetchRentEstimatesWorker], delay = 3600)`; `"analyzer"` → `WorkScheduler(StaleAnalysisWorkFinder, [AnalyzeListingWorker], delay = 60)`.

- [ ] **Step 1: Failing tests** (`testApplication` with `TestDatabase.dataSource`, `truncateAll` in `@Before`):
  - `profile_get_shows_defaults_when_empty` (200, body contains `name="annualIncome"` and `118000`)
  - `profile_post_saves_and_redirects` (303 → `/report`; `findFirst()!!.annualIncome == 125000.0`)
  - `profile_post_rejects_invalid_values` (credit 900 → 400, body contains "credit", `findFirst() == null`)
  - `report_lists_ranked_rows_and_pending_count` (seed 2 analyzed + 1 unanalyzed listing → body has both addresses in cost order and "1 pending")
  - `listing_detail_shows_buyer_and_lender_sections` (contains "Buyer view" and "Lender view")
  - `api_listings_returns_json_sorted` (`Content-Type` json, first `netMonthlyCost` ≤ second)
  - `api_profile_put_validates` (400 with `errors`)
  - `health_reports_ok_and_counts`
- [ ] **Step 2: Run, expect failure.** Step 3: implement (Freemarker tables; minimal CSS in `static/styles/app.css`; no JS). Step 4: PASS.
- [ ] **Step 5: Commit** — `feat(web): profile form, ranked report, listing detail, JSON api, health`

---

### Task 11: Wire-up, local run, Render deploy, README

**Files:**
- Modify: `applications/data-collector-server/src/main/kotlin/.../App.kt`, `applications/data-analyzer-server/.../App.kt` (use `Dependencies` + `Workers.start` with their one worker set; keep `/health`), `applications/*/build.gradle.kts` (deps), `docker-compose.yaml` (add `postgres:16` service + `DATABASE_URL`, `RENTCAST_API_KEY` passthrough), `Dockerfile` (unchanged jars; ensure `RUN_WORKERS` passthrough), `README.md`
- Create: `render.yaml`, `scripts/dev.ps1` (sets JAVA_HOME to Temurin 21, creates `househack` + `househack_test` DBs if missing, runs `gradlew build`)
- Test: existing `AppTest`s updated to assert `/health` 200 for collector and analyzer servers.

- [ ] **Step 1: Update the two worker apps** — `main()` reads `DATABASE_URL`, `RENTCAST_API_KEY`, runs `Migrations.run`, starts its scheduler, serves `/health`. `basic-server` `main()` additionally reads `RUN_WORKERS` (comma list) and passes it to `module(startWorkers = …)`. Missing `RENTCAST_API_KEY` → log a warning and use `FakeRentCastClient` (so the free demo deploy still shows data).
- [ ] **Step 2: `render.yaml`** — `services: [{type: web, name: househack-web, runtime: docker, plan: free, envVars: [{key: APP, value: /opt/applications/basic-server.jar}, {key: RUN_WORKERS, value: "collector,analyzer"}, {key: DATABASE_URL, fromDatabase: {name: househack-db, property: connectionString}}, {key: RENTCAST_API_KEY, sync: false}]}]`, `databases: [{name: househack-db, plan: free, databaseName: househack, user: househack}]`. Note in README that the free DB expires after 30 days and how to recreate it.
- [ ] **Step 3: Run everything locally** — `scripts/dev.ps1`, then start the three jars on 8888/8886/8887 with `DATABASE_URL=jdbc:postgresql://localhost:5432/househack?user=postgres&password=postgres` and no RentCast key (fake data). Expected: `/report` shows the 6 sample listings ranked within ~2 minutes; `/health` on all three returns 200.
- [ ] **Step 4: Deploy** — push to `main`; in Render "New → Blueprint" on the repo; set `RENTCAST_API_KEY` in the dashboard (Ryan does this; never paste the key in chat). Verify `https://<service>.onrender.com/health` and `/report`.
- [ ] **Step 5: README** — replace the starter's text: what HouseHack is, local setup (JDK 21, Postgres, env vars), run, test, deploy, rubric mapping table (from `docs/course/project-brief.md`).
- [ ] **Step 6: Commit** — `feat: wire apps, compose, render blueprint, README`

---

## Self-review notes

- Spec coverage: profile ✓(2,10), collector ✓(8,9), analyzer ✓(5–7), web + REST ✓(10), persistence ✓(1,3), unit tests ✓(every task), production ✓(11), budget rule ✓(3,8), two-view separation ✓(5), program stacking ✓(6), tax basis ✓(5), BCPAO filter ✓(9), RUN_WORKERS ✓(10,11), free-tier fallback to fake client ✓(11). Alerts/email, CI beyond tests, monitoring metrics, CD are B/A-level and get their own plan.
- Names used consistently: `ListingRepository.findStaleForAnalysis/markAnalyzed/findWithoutRentEstimate`, `ApiBudgetRepository.tryReserve`, `FinancingCalculator.buyerView/lenderView`, `ProgramRuleEngine.evaluate`, `ListingAnalyzer.analyze`, `Workers.start`.
- Known uncertainty: Task 9 depends on the real WebData.zip format; the plan stops for a decision if it is MDB.
