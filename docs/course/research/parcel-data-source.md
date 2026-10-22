# Parcel data source (decided 2026-10-21)

**BCPAO bulk download is not machine-fetchable.** `https://www.bcpao.us/Docs/PublicDownload/PropData/WebData.zip` returns HTTP 403 with a Cloudflare "Just a moment..." challenge for non-browser clients (verified with curl, 2026-10-21). A browser download works, but a scheduled worker cannot do that.

**Used instead: Florida DOR statewide cadastral layer (FGIO).**
- Service: `https://services9.arcgis.com/Gh9awoU677aKree0/arcgis/rest/services/Florida_Statewide_Cadastral/FeatureServer/0` (public, JSON, maxRecordCount 2000, pagination supported). Dataset page: https://www.floridagio.gov/datasets/FGIO::florida-statewide-parcels/about ; field definitions: https://fgio.maps.arcgis.com/home/item.html?id=55e830fd6c8948baae1601fbfc33a3b2
- Data = county tax rolls (NAL) joined to parcel polygons; refreshed annually in August, maintenance in May/November.
- Fields used: `PARCEL_ID`, `DOR_UC` (3-digit DOR land use; `'008'` = multi-family fewer than 10 units, `'001'` single family, `'004'` condo, `'003'` multi-family 10+), `NO_RES_UNT` (residential units), `JV` (just value), `AV_SD` (assessed, school district), `TV_SD` (taxable), `PHY_ADDR1`, `PHY_CITY`, `PHY_ZIPCD` (int), `ACT_YR_BLT`, `TAX_AUTH_C` (taxing authority code; millage not included), `TOT_LVG_AR`, `SALE_PRC1`/`SALE_YR1`.
- Query behaviour measured 2026-10-21: attribute-only `where=CO_NO=15 ...` times out (~55 s → HTTP 400; CO_NO is not indexed). Spatial envelope queries succeed: 0.02° box ≈ 1,227 parcels in 19 s; 0.1° box with `DOR_UC='008'` ≈ 536 parcels in 24 s. Point-in-parcel lookup ≈ 3 s.
- Geometry parameter must be JSON, e.g. `geometry={"xmin":-80.8,"ymin":28.3,"xmax":-80.7,"ymax":28.4,"spatialReference":{"wkid":4326}}&geometryType=esriGeometryEnvelope&inSR=4326&spatialRel=esriSpatialRelIntersects&returnGeometry=false&outFields=...&f=json`.
- Brevard County's own ArcGIS server (gis.brevardfl.gov) publishes zoning/FLU but no parcel layer (checked 2026-10-21).

**Still missing:** millage by taxing authority (TAX_AUTH_C → mills). Until a millage table is loaded, the analyzer uses the 0.016 county-average default and flags it.
