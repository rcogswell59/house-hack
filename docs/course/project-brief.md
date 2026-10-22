# HouseHack — project brief

Last updated: 2026-10-21

## The product in one paragraph
HouseHack is a personal house-hacking agent for first-time buyers in Brevard County, FL. Listing sites tell you a property's price; they don't tell you what you would actually pay each month after tenants in the other units pay rent, with every assistance program you qualify for applied. HouseHack collects active 2–4 unit listings daily, enriches them with county parcel and tax data, models the buyer's actual financing (FHA 3.5% down, homestead exemption on the owner's unit only, Florida insurance, reserves), checks eligibility for Florida and Brevard programs (Hometown Heroes, Brevard HFA, SHIP), and ranks properties by **net monthly housing cost**. It also runs the lender's math separately (FHA 75% rent rule, self-sufficiency test for 3–4 units) so a buyer knows both "what will I pay" and "will I get approved."

## Who it's for
First version: one profile — Ryan. First-time buyer, ~$118k base income, 700+ credit, under $25k saved, works on-site at a Florida employer near Cape Canaveral, wants a 2–4 unit property within ~30 min drive (Cape Canaveral, Cocoa Beach, Merritt Island, Cocoa, Rockledge, Titusville). Everything is profile-driven so a second user is a new profile row, not new code.

## Decisions made (2026-10-21)
| Decision | Choice | Why |
|---|---|---|
| Project (vs arbitrage model, vs meal-prep app) | Real estate house-hack agent | Fits collector→analyzer→report architecture; clear audience; genuinely unique; Ryan will use it. Arbitrage would expose private code; meal-prep is a crowded space. |
| Ranking metric | Net monthly housing cost (buyer view) | "How cheap can I live." Cash-to-close shown alongside. |
| Lender vs buyer math | Two separate outputs, never blended | FHA 75% rule is a qualifying rule, not a cost. Buyer view uses 100% rent minus tunable vacancy (default 8%) and maintenance (default 5–10%) reserves. |
| Geography | Workplace coords + max drive minutes on profile; v1 default 30 min from Cape Canaveral | Drive time is a profile field, not hard-coded. |
| Property types | 2, 3, 4 units | SFH+ADU/room-rent and rehab/203(k) deferred. |
| AI | None in v1 | Keeps C-level items deterministic and testable. Per-listing plain-English summaries are a planned A-level addition. |
| Data: listings + rent | RentCast API, free tier (50 req/month) | Only legal programmatic source found with sale listings + rent AVM; licensing allows storage and display. |
| Data: parcels/taxes | Florida DOR statewide cadastral FeatureServer (ArcGIS REST; point lookups + daily multi-family tile sweep). *Changed 2026-10-21:* the BCPAO bulk download is behind a Cloudflare bot challenge. See research/parcel-data-source.md | Free, machine-fetchable; supplies unit counts (NO_RES_UNT), just/assessed value, year built. Millage not included (county-average default until a millage table is loaded). |
| Persistence | PostgreSQL | Render free tier; standard for the course. |
| Deploy | Render free tier: one web service running both schedulers in-process (`RUN_WORKERS`) + free Postgres | Free workers don't exist on Render; locally and in compose the three processes stay separate. |
| Ops | Daily collector run; ranked report page; email alert when a listing enters top 10 (later) | Alerts become the "event collaboration/messaging" A-level item. |
| Tests | JUnit; RentCast client mocked/faked | B-level "test doubles" item. |
| Timeline assumption | Buying in 6–12 months | Program limits are parameterized by year (2026 now; 2027 when published). |

## Rubric mapping (see docs/assignments/rubric.md)
- Web application: Ktor + Freemarker profile form + ranked report.
- Data collection: data-collector worker (RentCast + BCPAO).
- Data analyzer: data-analyzer worker (financing + program rules + scoring).
- Unit tests: JUnit on analyzer math and rule engine.
- Data persistence: Postgres.
- REST collaboration: web app ↔ workers via REST (`/api/listings`, `/api/profile`), plus external RentCast API.
- Production environment: Render.
- Integration tests: Ktor testApplication + DB.
- Test doubles: fake RentCast client, fake clock.
- CI: GitHub Actions.
- Monitoring: structured JSON logs (starter has logging-support) + health endpoints + request metrics.
- Event messaging: new-top-10 alert events → email.
- CD: Render auto-deploy from main.

## Open questions
- Ryan's Hometown Heroes eligibility: on-site FL employer is necessary but the program is occupation-gated (healthcare, school, first responders, public safety, child care, military/veterans). Treated as a profile flag; app reports honestly.
- Whether arbitrage income counts toward program limits (needs 2-yr documented history; would push toward the $149,400 Brevard cap).
- Florida Amendment 3 (ballot 2026-11-03) could raise the homestead exemption; model as a year-keyed parameter.
