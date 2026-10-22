# Research notes: programs, loan rules, data sources

All accessed 2026-10-21 via Firecrawl. Unverified items are marked.

## Data sources

### RentCast API — primary listings + rent feed
- Free Developer plan: 50 API requests/month, no card. Paid "Scale" $449/mo for 25k requests (per api.market roundup; verify on rentcast.io/api before upgrading).
- Endpoints: property records (150M+), value + rent AVM with comps, active sale and rental listings filterable by city/area/type, zip-level market trends.
- Licensing permits consumer apps, internal storage, and distribution to end users; no attribution required.
- Docs: https://developers.rentcast.io/reference/introduction (llms.txt at https://developers.rentcast.io/llms.txt; append `.md` to any doc URL).
- Implication: collector must batch and cache. One listings pull/day for Brevard 2–4 unit ≈ 30 calls/month; rent estimates must be fetched only for new listings.

### Brevard County Property Appraiser (BCPAO) — free parcel enrichment
- `WebData.zip` countywide property database (~225 MB, updated daily); `Parcels.zip` shapefile; file geodatabases.
- https://www.bcpao.us/PublicData.aspx
- Also: Brevard GIS Open Data Hub (ArcGIS) https://brevard-gis-open-data-hub-brevardbocc.hub.arcgis.com/ ; Florida statewide parcels (FL GIO).
- Use: assessed value, millage/taxes, year built, unit count, zoning, flood zone (via GIS) to enrich listings without spending RentCast calls.

### Rejected
- Zillow: no public API; scraping violates ToS.
- Redfin Data Center: aggregate market CSVs only, no listings.

## Loan rules

### FHA 2–4 unit, owner-occupied
- 3.5% down with 580+ score. Must occupy one unit.
- Qualifying rental income: 75% of appraiser's market rent for non-owner units (25% vacancy/maintenance haircut).
- **Self-sufficiency test (3–4 units only):** 75% of total market rent (all units, incl. owner's) ≥ full monthly PITI.
- 2026 Brevard FHA limits: 1-unit $541,287 · 2-unit $693,050 · 3-unit $837,700 · 4-unit $1,041,125.
- Sources: https://www.neighborsbank.com/learn/fha-multifamily-loans/ ; https://www.amerisave.com/learn/fha-multifamily-loans-complete-guide-to-financing-multiunit-properties ; https://www.fha.com/lending_limits_state?state=Florida ; HUD lookup https://entp.hud.gov/idapp/html/hicostlook.cfm

## Assistance programs

### Florida Hometown Heroes (FHFC)
- 5% of first-mortgage amount, $10k min / $35k max, 0% deferred second mortgage, no monthly payment.
- Occupation-gated: full-time at a FL-based employer as healthcare worker, school staff, first responder, public safety/court employee, child care worker; or servicemember; or veteran employed full-time by FL employer. Remote/out-of-state employees excluded; hybrid OK if on-site ≥3 days/week.
- First-time buyer (veterans exempt), 640+ score, income ≤ county limit. Brevard 2026: borrower income (TBA) $149,400; household income (Bond) $101,414 (1–2 person) / $116,627 (3+).
- Two flavors: TBA (borrower income only) vs Bond (whole-household income 18+).
- Funding first-come, first-served; can run out.
- Sources: https://www.floridahousing.org/live-local-act/hometown-heroes-program ; limits PDF https://www.floridahousing.org/docs/default-source/programs/homebuyers/hometown-heroes/2026-hometown-heroes-limits-rev-7-13-2026.pdf ; https://www.makefloridayourhome.com/learn/florida-hometown-heroes-income-limits ; https://www.ehousingplus.com/homeownership/florida-housing-finance-corporation/program-highlights/

### Brevard County SHIP Purchase Assistance
- Up to $75,000 (≤50% AMI) or $60,000 (51–80% AMI), 0% deferred second mortgage; "minimum needed," never over-subsidized.
- First-time buyer (no ownership in 3 yrs), liquid assets ≤ $15,000, front/back DTI 34%/42%, 5-hour homebuyer class, min contribution $500/$1,000.
- Administered by Community Housing Initiative (C.H.I.), (321) 253-0053.
- Source: http://www.brevardfl.gov/HousingAndHumanServices/HousingPrograms/PurchaseAssistanceProgram
- Note: Ryan at ~$118k is far above 80% AMI for Brevard → not eligible; still modeled for other profiles.

### Brevard County Housing Finance Authority
- 30-yr fixed first mortgage + up to $15,000 DPA as 0% deferred 30-yr second; never forgiven; due on sale/refi/move-out.
- Source: https://brevardhfa.org/
- Income/price limits not captured yet (unverified).

### Florida homestead exemption + Save Our Homes
- Up to $50,000 off assessed value; 3%/yr assessment cap after base year.
- **Multi-unit:** exemption applies only to the owner-occupied portion; rented units assessed as non-homestead (10% cap).
- First-time FL buyer guide: PT-107 https://floridarevenue.com/property/Documents/pt107.pdf ; exemptions page https://floridarevenue.com/property/Pages/Taxpayers_Exemptions.aspx ; duplex note https://taxattorneylegal.com/tax-lawyer-near-me/f/florida-homestead-exemption-for-a-duplex-with-rental
- **Amendment 3 (ballot 2026-11-03):** would raise homestead exemption to $250k for non-school levies and cut non-homestead cap 10%→5%. https://floridataxwatch.org/research/florida-property-tax-resource-center/ — model as year-keyed parameter.

## Still to research
- Brevard millage rates by city; FL homeowners + flood insurance cost model for beachside vs mainland.
- Brevard HFA income/price limits.
- 2026 FHA MIP rates (upfront 1.75%, annual ~0.55% — verify).
- Drive-time source (OSRM public server vs straight-line haversine for v1).

## RentCast API details (accessed 2026-10-21, https://developers.rentcast.io/reference/*.md)
- Base `https://api.rentcast.io/v1`, header `X-Api-Key`.
- `GET /listings/sale?latitude=&longitude=&radius=<mi, max 100>&propertyType=Multi-Family&status=Active&limit=500&offset=0` — paginated, 500/page, sorted by lastSeenDate desc. One call covers the whole 30-mile Cape Canaveral radius (Brevard multi-family inventory is well under 500).
- Listing fields: id, formattedAddress, city, zipCode, county, latitude, longitude, propertyType, bedrooms, bathrooms, squareFootage, lotSize, yearBuilt, hoa.fee, status, price, listedDate, daysOnMarket, mlsNumber, listingAgent, history.
- **No unit-count field.** Unit count must come from BCPAO parcel data (land-use code / living units) or be inferred from bedrooms (fallback, flagged). → bcpao-import is required for C-level, not optional enrichment.
- `GET /avm/rent/long-term?address=...&propertyType=Multi-Family&bedrooms=&bathrooms=&squareFootage=` → `{rent, rentRangeLow, rentRangeHigh, comparables[]}`. **For Multi-Family returns a single-unit estimate**, not the building. Per-unit bed/bath unknown from listing → estimate using (bedrooms ÷ units) rounded, flagged as approximation.
- Budget math: 1 listings call/day ≈ 30/month; leaves ~20 rent-estimate calls/month for new listings.
