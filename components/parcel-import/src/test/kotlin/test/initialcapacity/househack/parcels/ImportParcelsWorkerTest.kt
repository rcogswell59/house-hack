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
    fun link_continues_when_a_point_lookup_throws() {
        val boom = listings.upsertSeen(listing("boom", "1 Boom St", 28.30, -80.70), now)
        val fine = listings.upsertSeen(listing("fine", "1209 N Indian River Dr", 28.3627, -80.7276), now)
        source.failPoints = setOf(28.30 to -80.70)
        source.byPoint[28.3627 to -80.7276] = fact("P-A", "1209 N INDIAN RIVER DR")

        ImportParcelsWorker(source, parcels, listings, profiles, sweep = false).execute(task)

        assertNull(parcels.findForListing(boom.id!!))
        assertEquals("P-A", parcels.findForListing(fine.id!!)?.parcelId)
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
    fun worker_without_profile_throws_so_the_day_is_not_marked_done() {
        TestDatabase.truncateAll()
        listings.upsertSeen(listing("a", "1 Main St", 28.36, -80.72), now)

        val e = runCatching { ImportParcelsWorker(source, parcels, listings, profiles, sweep = true).execute(task) }.exceptionOrNull()

        assertTrue(e is IllegalStateException, "expected IllegalStateException, got $e")
        assertTrue(source.pointLookups.isEmpty() && source.sweptTiles.isEmpty())
    }
}

class ImportParcelsWorkerSweepTest {
    private val db = io.initialcapacity.househack.db.DatabaseTemplate(TestDatabase.dataSource)
    private val listings = ListingRepository(db)
    private val parcels = ParcelFactRepository(db)
    private val profiles = ProfileRepository(db)
    private val source = FakeParcelSource()
    private val task = CollectorTask("sweep-parcels", LocalDate.of(2026, 10, 21))

    @Before
    fun setUp() {
        TestDatabase.truncateAll()
        profiles.save(BuyerProfile.defaults())
    }

    @Test
    fun link_false_skips_point_lookups_and_only_sweeps() {
        listings.upsertSeen(Listing(null, "rentcast", "a", "1 Main St", "Cocoa", "32922", 28.36, -80.72, 400_000.0, 4, 2.0, 1800, 1975,
            "Multi-Family", null, null, null, true, null, "{}"), Instant.parse("2026-10-21T06:00:00Z"))

        ImportParcelsWorker(source, parcels, listings, profiles, link = false, sweep = true, tileDegrees = 0.5).execute(task)

        assertTrue(source.pointLookups.isEmpty())
        assertTrue(source.sweptTiles.isNotEmpty())
    }

    @Test
    fun failed_tile_is_split_into_quadrants_and_retried() {
        source.failWhen = { it.maxLng - it.minLng > 0.3 }   // every full-size tile fails, quadrants succeed
        source.sweepResults = listOf(ParcelFact("Q", "1 Q ST", "COCOA", "32922", 1.0, 1.0, null, null, 1970, 2, "008", null))

        ImportParcelsWorker(source, parcels, listings, profiles, link = false, sweep = true, tileDegrees = 0.5).execute(task)

        val big = source.sweptTiles.count { it.maxLng - it.minLng > 0.3 }
        val small = source.sweptTiles.count { it.maxLng - it.minLng <= 0.3 }
        assertTrue(big > 0 && small == big * 4, "expected 4 quadrants per failed tile, got big=$big small=$small")
        assertNotNull(parcels.findByAddressNorm("1 Q ST"))
    }
}
