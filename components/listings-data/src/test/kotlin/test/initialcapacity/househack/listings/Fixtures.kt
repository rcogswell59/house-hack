package test.initialcapacity.househack.listings

import io.initialcapacity.househack.db.DatabaseTemplate
import io.initialcapacity.househack.listings.Analysis
import io.initialcapacity.househack.listings.Listing
import io.initialcapacity.househack.listings.ParcelFact
import test.initialcapacity.househack.db.TestDatabase
import java.time.Instant

object Fixtures {
    val db = DatabaseTemplate(TestDatabase.dataSource)
    val t0: Instant = Instant.parse("2026-10-01T12:00:00Z")

    fun listing(sourceId: String = "L1", address: String = "123 N Atlantic Ave", price: Double = 400_000.0, bedrooms: Int? = 4) = Listing(
        id = null, source = "rentcast", sourceId = sourceId, address = address, city = "Cape Canaveral", zip = "32920",
        lat = 28.39, lng = -80.60, price = price, bedrooms = bedrooms, bathrooms = 2.0, sqft = 1800, yearBuilt = 1975,
        propertyType = "Multi-Family", listedAt = t0, firstSeen = null, lastSeen = null, active = true, lastAnalyzedAt = null,
        raw = """{"id":"$sourceId"}""",
    )

    fun parcel(parcelId: String = "P1", addressNorm: String = "123 N ATLANTIC AVE", units: Int? = 2) = ParcelFact(
        parcelId = parcelId, addressNorm = addressNorm, city = "CAPE CANAVERAL", zip = "32920", justValue = 380_000.0,
        assessedValue = 300_000.0, taxesAnnual = 4_800.0, millage = 0.016, yearBuilt = 1975, units = units, landUseCode = "08", importedAt = null,
    )

    fun analysis(listingId: Long, profileId: Long, netMonthlyCost: Double) = Analysis(
        id = null, listingId = listingId, profileId = profileId, runAt = null, netMonthlyCost = netMonthlyCost, cashToClose = 11_000.0,
        lenderPass = true, selfSufficiencyRatio = null, dti = 0.22, driveMinutes = 12.5, breakdownJson = "{}", programResultsJson = "[]",
    )

    /** Inserts a minimal profile row directly so repository tests don't depend on the profile module. */
    fun insertProfile(): Long = db.queryOne(
        "insert into profiles (name, annual_income, liquid_savings, credit_score, first_time_buyer, workplace_lat, workplace_lng, max_drive_minutes) " +
            "values ('t', 100000, 10000, 700, true, 28.39, -80.60, 30) returning id"
    ) { it.getLong(1) }!!
}
