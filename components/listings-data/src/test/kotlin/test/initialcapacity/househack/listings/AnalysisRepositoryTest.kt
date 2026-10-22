package test.initialcapacity.househack.listings

import io.initialcapacity.househack.listings.AnalysisRepository
import io.initialcapacity.househack.listings.ListingRepository
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import kotlin.test.assertEquals

class AnalysisRepositoryTest {
    private val analyses = AnalysisRepository(Fixtures.db)
    private val listings = ListingRepository(Fixtures.db)

    @Before
    fun setUp() = TestDatabase.truncateAll()

    @Test
    fun upsert_replaces_for_same_listing_and_profile() {
        val profileId = Fixtures.insertProfile()
        val listing = listings.upsertSeen(Fixtures.listing(), Fixtures.t0)

        val first = analyses.upsert(Fixtures.analysis(listing.id!!, profileId, 2000.0))
        val second = analyses.upsert(Fixtures.analysis(listing.id!!, profileId, 1500.0))

        assertEquals(first.id, second.id)
        assertEquals(1500.0, analyses.findForListing(listing.id!!, profileId)?.netMonthlyCost)
        assertEquals(1, Fixtures.db.queryOne("select count(*) from analyses") { it.getInt(1) })
    }

    @Test
    fun rankedForProfile_orders_by_net_cost_asc_and_skips_inactive() {
        val profileId = Fixtures.insertProfile()
        val cheap = listings.upsertSeen(Fixtures.listing("cheap"), Fixtures.t0)
        val pricey = listings.upsertSeen(Fixtures.listing("pricey"), Fixtures.t0)
        val gone = listings.upsertSeen(Fixtures.listing("gone"), Fixtures.t0.minusSeconds(864_000))
        listOf(cheap to 1200.0, pricey to 2500.0, gone to 100.0).forEach { (l, cost) -> analyses.upsert(Fixtures.analysis(l.id!!, profileId, cost)) }
        listings.markInactiveNotSeenSince("rentcast", Fixtures.t0.minusSeconds(60))

        val ranked = analyses.rankedForProfile(profileId)

        assertEquals(listOf("cheap", "pricey"), ranked.map { it.second.sourceId })
        assertEquals(1200.0, ranked.first().first.netMonthlyCost)
    }

    @Test
    fun countPendingForProfile_counts_unanalyzed_active() {
        val profileId = Fixtures.insertProfile()
        val done = listings.upsertSeen(Fixtures.listing("done"), Fixtures.t0)
        listings.upsertSeen(Fixtures.listing("pending"), Fixtures.t0)
        analyses.upsert(Fixtures.analysis(done.id!!, profileId, 1800.0))

        assertEquals(1, analyses.countPendingForProfile(profileId))
    }
}
