package test.initialcapacity.househack.analyzer

import io.initialcapacity.househack.analyzer.ListingAnalyzer
import io.initialcapacity.househack.analyzer.ProgramRule
import io.initialcapacity.househack.analyzer.ProgramRuleEngine
import io.initialcapacity.househack.listings.Listing
import io.initialcapacity.househack.listings.ParcelFact
import io.initialcapacity.househack.listings.RentEstimate
import io.initialcapacity.househack.profile.BuyerProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingAnalyzerTest {
    private val rules = listOf(
        ProgramRule("FHA_LIMITS", 2026, "Brevard", Json.parseToJsonElement("""{"2": 693050, "3": 837700, "4": 1041125}""").jsonObject),
        ProgramRule("BREVARD_HFA", 2026, "Brevard", Json.parseToJsonElement("""{"exclusive_group": "first_mortgage_dpa", "amount": 15000, "requires_first_time": true}""").jsonObject),
    )
    private val analyzer = ListingAnalyzer(ProgramRuleEngine(rules))
    private val ryan = BuyerProfile.defaults().copy(id = 1)

    // 215 Dixon Blvd, Cocoa: ~23 min from the Cape Canaveral workplace
    private val listing = Listing(
        id = 10, source = "rentcast", sourceId = "L", address = "215 Dixon Blvd", city = "Cocoa", zip = "32922", lat = 28.3861, lng = -80.7420,
        price = 400_000.0, bedrooms = 4, bathrooms = 2.0, sqft = 1800, yearBuilt = 1975, propertyType = "Multi-Family",
        listedAt = null, firstSeen = null, lastSeen = null, active = true, lastAnalyzedAt = null, raw = "{}",
    )
    private val parcel = ParcelFact("P", "215 DIXON BLVD", "COCOA", "32922", 380_000.0, 300_000.0, 4_800.0, 0.016, 1975, 2, "08", null)
    private val rent = RentEstimate(10, 1_800.0, 1_700.0, 1_900.0, 2, null)

    @Test
    fun analyze_golden_duplex_matches_calculator() {
        val a = analyzer.analyze(ryan, listing, parcel, rent)

        assertNotNull(a)
        assertEquals(10, a.listingId)
        assertEquals(1, a.profileId)
        assertEquals(1963.2, a.netMonthlyCost, 1.5)
        assertEquals(11_000.0, a.cashToClose, 0.01)
        assertTrue(a.lenderPass)
        assertNull(a.selfSufficiencyRatio)
        assertEquals(0.222, a.dti, 0.002)
        assertEquals(22.9, a.driveMinutes, 0.5)

        val breakdown = Json.parseToJsonElement(a.breakdownJson).jsonObject
        assertEquals("parcel", breakdown["unitsSource"]!!.jsonPrimitive.content)
        assertEquals("parcel", breakdown["millageSource"]!!.jsonPrimitive.content)
        assertEquals(2, breakdown["units"]!!.jsonPrimitive.content.toInt())
        assertTrue(breakdown.containsKey("buyer") && breakdown.containsKey("lender"))
        assertTrue(a.programResultsJson.contains("BREVARD_HFA"))
    }

    @Test
    fun analyze_returns_null_when_rent_estimate_missing() {
        assertNull(analyzer.analyze(ryan, listing, parcel, rent = null))
    }

    @Test
    fun analyze_skips_listings_outside_2_to_4_units() {
        assertNull(analyzer.analyze(ryan, listing, parcel.copy(units = 1), rent))
        assertNull(analyzer.analyze(ryan, listing, parcel.copy(units = 5), rent))
    }

    @Test
    fun analyze_uses_bedroom_fallback_when_no_parcel() {
        val a = analyzer.analyze(ryan, listing, parcel = null, rent = rent)

        assertNotNull(a)
        val breakdown = Json.parseToJsonElement(a.breakdownJson).jsonObject
        assertEquals("bedrooms_fallback", breakdown["unitsSource"]!!.jsonPrimitive.content)
        assertEquals("default", breakdown["millageSource"]!!.jsonPrimitive.content)
        assertEquals(2, breakdown["units"]!!.jsonPrimitive.content.toInt())
        assertEquals(1963.2, a.netMonthlyCost, 1.5)   // default millage is 0.016, same as the parcel
    }

    @Test
    fun analyze_uses_bedroom_fallback_when_parcel_units_is_zero_or_null() {
        for (units in listOf(0, null)) {
            val a = analyzer.analyze(ryan, listing, parcel.copy(units = units), rent)

            assertNotNull(a, "units=$units should fall back to bedrooms")
            val breakdown = Json.parseToJsonElement(a.breakdownJson).jsonObject
            assertEquals("bedrooms_fallback", breakdown["unitsSource"]!!.jsonPrimitive.content)
            assertEquals("parcel", breakdown["millageSource"]!!.jsonPrimitive.content)  // parcel still supplies millage
        }
    }

    @Test
    fun analyze_returns_null_without_parcel_or_bedrooms() {
        assertNull(analyzer.analyze(ryan, listing.copy(bedrooms = null), parcel = null, rent = rent))
    }

    @Test
    fun analyze_excludes_beyond_max_drive() {
        val orlando = listing.copy(lat = 28.5383, lng = -81.3792)
        assertNull(analyzer.analyze(ryan, orlando, parcel, rent))
    }

    @Test
    fun analyze_returns_null_without_coordinates() {
        assertNull(analyzer.analyze(ryan, listing.copy(lat = null), parcel, rent))
    }
}
