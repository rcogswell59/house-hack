package test.initialcapacity.househack.listings

import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.listings.ParcelFactRepository
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParcelFactRepositoryTest {
    private val parcels = ParcelFactRepository(Fixtures.db)
    private val listings = ListingRepository(Fixtures.db)

    @Before
    fun setUp() = TestDatabase.truncateAll()

    @Test
    fun upsertAll_then_findByAddressNorm() {
        assertEquals(2, parcels.upsertAll(listOf(Fixtures.parcel("P1"), Fixtures.parcel("P2", "456 OCEAN BLVD", units = 3))))
        parcels.upsertAll(listOf(Fixtures.parcel("P1").copy(units = 4)))

        val found = parcels.findByAddressNorm("123 N ATLANTIC AVE")

        assertNotNull(found)
        assertEquals(4, found.units)
        assertEquals(0.016, found.millage)
        assertEquals("08", found.landUseCode)
        assertNull(parcels.findByAddressNorm("nowhere"))
    }

    @Test
    fun linkListing_then_findForListing() {
        val listing = listings.upsertSeen(Fixtures.listing(), Fixtures.t0)
        parcels.upsertAll(listOf(Fixtures.parcel("P1")))

        assertNull(parcels.findForListing(listing.id!!))
        parcels.linkListing(listing.id!!, "P1")
        parcels.linkListing(listing.id!!, "P1") // idempotent

        assertEquals("P1", parcels.findForListing(listing.id!!)?.parcelId)
    }
}
