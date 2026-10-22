package test.initialcapacity.househack.listings

import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.listings.ParcelFactRepository
import io.initialcapacity.househack.listings.RentEstimate
import io.initialcapacity.househack.listings.RentEstimateRepository
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListingRepositoryTest {
    private val listings = ListingRepository(Fixtures.db)
    private val rents = RentEstimateRepository(Fixtures.db)
    private val parcels = ParcelFactRepository(Fixtures.db)
    private val t0 = Fixtures.t0

    @Before
    fun setUp() = TestDatabase.truncateAll()

    @Test
    fun upsertSeen_inserts_then_updates_price_and_last_seen() {
        val first = listings.upsertSeen(Fixtures.listing(price = 400_000.0), seenAt = t0)
        val second = listings.upsertSeen(Fixtures.listing(price = 390_000.0), seenAt = t0.plus(Duration.ofDays(1)))

        assertEquals(first.id, second.id)
        assertEquals(390_000.0, second.price)
        assertEquals(t0, second.firstSeen)
        assertEquals(t0.plus(Duration.ofDays(1)), second.lastSeen)
        assertEquals(1, listings.findActive().size)
    }

    @Test
    fun markInactiveNotSeenSince_flags_old_rows() {
        listings.upsertSeen(Fixtures.listing("old"), seenAt = t0)
        listings.upsertSeen(Fixtures.listing("new"), seenAt = t0.plus(Duration.ofDays(5)))

        val flagged = listings.markInactiveNotSeenSince("rentcast", cutoff = t0.plus(Duration.ofDays(2)))

        assertEquals(1, flagged)
        assertEquals(listOf("new"), listings.findActive().map { it.sourceId })
    }

    @Test
    fun findWithoutRentEstimate_excludes_estimated_and_inactive() {
        val a = listings.upsertSeen(Fixtures.listing("a"), t0)
        val b = listings.upsertSeen(Fixtures.listing("b"), t0)
        listings.upsertSeen(Fixtures.listing("inactive"), t0.minus(Duration.ofDays(10)))
        listings.markInactiveNotSeenSince("rentcast", t0.minus(Duration.ofDays(1)))
        rents.save(RentEstimate(a.id!!, 1800.0, 1700.0, 1900.0, 2, null))

        assertEquals(listOf(b.id), listings.findWithoutRentEstimate(limit = 10).map { it.id })
    }

    @Test
    fun findStaleForAnalysis_returns_never_analyzed_and_profile_changed() {
        val never = listings.upsertSeen(Fixtures.listing("never"), t0)
        val analyzedBeforeProfile = listings.upsertSeen(Fixtures.listing("stale"), t0)
        listings.markAnalyzed(analyzedBeforeProfile.id!!, at = t0.plus(Duration.ofHours(1)))

        val stale = listings.findStaleForAnalysis(profileUpdatedAt = t0.plus(Duration.ofHours(2))).map { it.id }

        assertTrue(stale.contains(never.id))
        assertTrue(stale.contains(analyzedBeforeProfile.id))
    }

    @Test
    fun findStaleForAnalysis_excludes_fresh_but_includes_new_rent_or_parcel() {
        val fresh = listings.upsertSeen(Fixtures.listing("fresh"), t0)
        val newRent = listings.upsertSeen(Fixtures.listing("rent", address = "9 Rent St"), t0)
        val newParcel = listings.upsertSeen(Fixtures.listing("parcel", address = "9 Parcel St"), t0)
        val analyzedAt = t0.plus(Duration.ofHours(1))
        listOf(fresh, newRent, newParcel).forEach { listings.markAnalyzed(it.id!!, analyzedAt) }

        rents.save(RentEstimate(newRent.id!!, 1800.0, null, null, null, fetchedAt = analyzedAt.plus(Duration.ofMinutes(5))))
        parcels.upsertAll(listOf(Fixtures.parcel("P9", "9 PARCEL ST").copy(importedAt = analyzedAt.plus(Duration.ofMinutes(5)))))
        parcels.linkListing(newParcel.id!!, "P9")

        val stale = listings.findStaleForAnalysis(profileUpdatedAt = t0).map { it.id }

        assertFalse(stale.contains(fresh.id))
        assertTrue(stale.contains(newRent.id))
        assertTrue(stale.contains(newParcel.id))
    }
}
