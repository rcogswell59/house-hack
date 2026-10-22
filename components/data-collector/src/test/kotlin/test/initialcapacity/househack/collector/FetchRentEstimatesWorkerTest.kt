package test.initialcapacity.househack.collector

import io.initialcapacity.househack.collector.FetchRentEstimatesWorker
import io.initialcapacity.househack.db.DatabaseTemplate
import io.initialcapacity.househack.listings.ApiBudgetRepository
import io.initialcapacity.househack.listings.Listing
import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.listings.ParcelFact
import io.initialcapacity.househack.listings.ParcelFactRepository
import io.initialcapacity.househack.listings.RentEstimateRepository
import io.initialcapacity.househack.rentcast.FakeRentCastClient
import io.initialcapacity.workflow.CollectorTask
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FetchRentEstimatesWorkerTest {
    private val db = DatabaseTemplate(TestDatabase.dataSource)
    private val listings = ListingRepository(db)
    private val rents = RentEstimateRepository(db)
    private val parcels = ParcelFactRepository(db)
    private val budget = ApiBudgetRepository(db)
    private val client = FakeRentCastClient()
    private val now = Instant.parse("2026-10-21T06:00:00Z")
    private val month = LocalDate.of(2026, 10, 1)
    private val task = CollectorTask("fetch-rents", LocalDate.of(2026, 10, 21))

    private fun worker(perRunCap: Int = 5, monthlyLimit: Int = 45) =
        FetchRentEstimatesWorker(client, listings, rents, parcels, budget, Clock.fixed(now, ZoneOffset.UTC), perRunCap, monthlyLimit)

    private fun listing(n: Int, bedrooms: Int? = 4) = Listing(
        null, "rentcast", "L$n", "$n Main St", "Cocoa", "32922", 28.38, -80.74, 400_000.0, bedrooms, 2.0, 1800, 1975,
        "Multi-Family", null, null, null, true, null, "{}",
    )

    @Before
    fun setUp() = TestDatabase.truncateAll()

    @Test
    fun fetch_rents_caps_per_run_and_counts_budget() {
        (1..6).forEach { listings.upsertSeen(listing(it), now) }

        worker(perRunCap = 5).execute(task)

        assertEquals(1, listings.findWithoutRentEstimate(10).size)
        assertEquals(5, budget.callsUsed("rentcast", month))
        assertEquals(5, client.calls.size)
        assertEquals(1800.0, rents.findForListing(listings.findActive().first().id!!)!!.estRentPerUnit)
    }

    @Test
    fun fetch_rents_passes_beds_per_unit_from_parcel_units() {
        val saved = listings.upsertSeen(listing(1, bedrooms = 6), now)
        parcels.upsertAll(listOf(ParcelFact("P", "1 MAIN ST", null, null, null, null, null, null, null, 3, "08", null)))
        parcels.linkListing(saved.id!!, "P")

        worker().execute(task)

        assertEquals(listOf("rentEstimate(bedrooms=2)"), client.calls)
        assertEquals(2, rents.findForListing(saved.id!!)!!.bedsAssumed)
    }

    @Test
    fun fetch_rents_assumes_two_units_without_parcel_and_null_beds_without_bedrooms() {
        val fourBed = listings.upsertSeen(listing(1, bedrooms = 4), now)
        val unknown = listings.upsertSeen(listing(2, bedrooms = null), now)

        worker().execute(task)

        assertEquals(2, rents.findForListing(fourBed.id!!)!!.bedsAssumed)
        assertNotNull(rents.findForListing(unknown.id!!))
        assertEquals(null, rents.findForListing(unknown.id!!)!!.bedsAssumed)
        assertEquals(listOf("rentEstimate(bedrooms=2)", "rentEstimate(bedrooms=null)"), client.calls)
    }

    @Test
    fun fetch_rents_stops_at_monthly_limit() {
        (1..3).forEach { listings.upsertSeen(listing(it), now) }
        budget.tryReserve("rentcast", month, 45, n = 44)

        worker(monthlyLimit = 45).execute(task)

        assertEquals(1, client.calls.size)
        assertEquals(45, budget.callsUsed("rentcast", month))
        assertEquals(2, listings.findWithoutRentEstimate(10).size)
    }
}
