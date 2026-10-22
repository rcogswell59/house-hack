package test.initialcapacity.househack.parcels

import io.initialcapacity.househack.db.DatabaseTemplate
import io.initialcapacity.househack.listings.Listing
import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.listings.ParcelFact
import io.initialcapacity.househack.listings.ParcelFactRepository
import io.initialcapacity.househack.parcels.FakeParcelSource
import io.initialcapacity.househack.parcels.ImportParcelsWorker
import io.initialcapacity.househack.profile.BuyerProfile
import io.initialcapacity.househack.profile.ProfileRepository
import io.initialcapacity.workflow.CollectorTask
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportParcelsWorkerTest {
    private val db = DatabaseTemplate(TestDatabase.dataSource)
    private val listings = ListingRepository(db)
    private val parcels = ParcelFactRepository(db)
    private val profiles = ProfileRepository(db)
    private val source = FakeParcelSource()
    private val task = CollectorTask("import-parcels", LocalDate.of(2026, 10, 21))
    private val now = Instant.parse("2026-10-21T06:00:00Z")

    private fun fact(id: String, address: String, units: Int = 2) =
        ParcelFact(id, address, "COCOA", "32922", 500_000.0, 400_000.0, null, null, 1975, units, "008", null)

    private fun listing(sourceId: String, address: String, lat: Double?, lng: Double?) = Listing(
        null, "rentcast", sourceId, address, "Cocoa", "32922", lat, lng, 400_000.0, 4, 2.0, 1800, 1975, "Multi-Family",
        null, null, null, true, null, "{}",
    )

    @Before
    fun setUp() {
        TestDatabase.truncateAll()
        profiles.save(BuyerProfile.defaults())
    }

    @Test
    fun worker_links_each_unlinked_listing_by_point_lookup() {
        val a = listings.upsertSeen(listing("a", "1209 N Indian River Dr", 28.3627, -80.7276), now)
        val b = listings.upsertSeen(listing("b", "999 Nowhere Rd", 28.40, -80.70), now)
        source.byPoint[28.3627 to -80.7276] = fact("P-A", "1209 N INDIAN RIVER DR")

        ImportParcelsWorker(source, parcels, listings, profiles, sweep = false).execute(task)

        assertEquals("P-A", parcels.findForListing(a.id!!)?.parcelId)
        assertNull(parcels.findForListing(b.id!!))
        assertEquals(listOf(28.3627 to -80.7276, 28.40 to -80.70), source.pointLookups)
    }

    @Test
    fun worker_falls_back_to_address_match_when_point_misses() {
        val a = listings.upsertSeen(listing("a", "1141 N. Indian River Drive", 28.36, -80.72), now)
        parcels.upsertAll(listOf(fact("P-ADDR", "1141 N INDIAN RIVER DR")))

        ImportParcelsWorker(source, parcels, listings, profiles, sweep = false).execute(task)

        assertEquals("P-ADDR", parcels.findForListing(a.id!!)?.parcelId)
    }

    @Test
    fun worker_skips_already_linked_listings() {
        val a = listings.upsertSeen(listing("a", "1 Main St", 28.36, -80.72), now)
        parcels.upsertAll(listOf(fact("P-OLD", "1 MAIN ST")))
        parcels.linkListing(a.id!!, "P-OLD")

        ImportParcelsWorker(source, parcels, listings, profiles, sweep = false).execute(task)

        assertTrue(source.pointLookups.isEmpty())
    }

    @Test
    fun sweep_tiles_profile_radius_and_upserts_multifamily_parcels() {
        source.sweepResults = listOf(fact("S1", "10 A ST"), fact("S2", "20 B ST", units = 3))

        ImportParcelsWorker(source, parcels, listings, profiles, sweep = true, tileDegrees = 0.5).execute(task)

        assertTrue(source.sweptTiles.isNotEmpty(), "sweep should query at least one tile")
        assertTrue(source.sweptTiles.all { it.minLat < 28.3922 + 0.5 && it.maxLat > 28.3922 - 0.5 })
        assertNotNull(parcels.findByAddressNorm("10 A ST"))
        assertEquals(3, parcels.findByAddressNorm("20 B ST")?.units)
        assertEquals(2, db.queryOne("select count(*) from parcel_facts") { it.getInt(1) })
    }

    @Test
    fun worker_does_nothing_without_profile() {
        TestDatabase.truncateAll()
        listings.upsertSeen(listing("a", "1 Main St", 28.36, -80.72), now)

        ImportParcelsWorker(source, parcels, listings, profiles, sweep = true).execute(task)

        assertTrue(source.pointLookups.isEmpty() && source.sweptTiles.isEmpty())
    }
}
