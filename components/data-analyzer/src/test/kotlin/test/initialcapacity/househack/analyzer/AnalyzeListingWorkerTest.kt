package test.initialcapacity.househack.analyzer

import io.initialcapacity.househack.analyzer.AnalyzeListingTask
import io.initialcapacity.househack.analyzer.AnalyzeListingWorker
import io.initialcapacity.househack.analyzer.ListingAnalyzer
import io.initialcapacity.househack.analyzer.ProgramRuleEngine
import io.initialcapacity.househack.analyzer.ProgramRuleRepository
import io.initialcapacity.househack.analyzer.StaleAnalysisWorkFinder
import io.initialcapacity.househack.db.DatabaseTemplate
import io.initialcapacity.househack.listings.AnalysisRepository
import io.initialcapacity.househack.listings.Listing
import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.listings.ParcelFact
import io.initialcapacity.househack.listings.ParcelFactRepository
import io.initialcapacity.househack.listings.RentEstimate
import io.initialcapacity.househack.listings.RentEstimateRepository
import io.initialcapacity.househack.profile.BuyerProfile
import io.initialcapacity.househack.profile.ProfileRepository
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnalyzeListingWorkerTest {
    private val db = DatabaseTemplate(TestDatabase.dataSource)
    private val listings = ListingRepository(db)
    private val parcels = ParcelFactRepository(db)
    private val rents = RentEstimateRepository(db)
    private val profiles = ProfileRepository(db)
    private val analyses = AnalysisRepository(db)
    private val analyzer = ListingAnalyzer(ProgramRuleEngine(ProgramRuleRepository(db).forYear(2026, "Brevard")))
    private val worker = AnalyzeListingWorker(analyzer, listings, parcels, rents, profiles, analyses)
    private val finder = StaleAnalysisWorkFinder(listings, profiles)
    private val t0 = Instant.parse("2026-10-01T12:00:00Z")

    private fun listing(sourceId: String, bedrooms: Int? = 4) = Listing(
        null, "rentcast", sourceId, "215 Dixon Blvd", "Cocoa", "32922", 28.3861, -80.7420, 400_000.0, bedrooms, 2.0, 1800, 1975,
        "Multi-Family", null, null, null, true, null, "{}",
    )

    @Before
    fun setUp() = TestDatabase.truncateAll()

    @Test
    fun worker_writes_analysis_and_marks_analyzed() {
        val profile = profiles.save(BuyerProfile.defaults())
        val saved = listings.upsertSeen(listing("a"), t0)
        parcels.upsertAll(listOf(ParcelFact("P", "215 DIXON BLVD", null, null, null, null, null, 0.016, null, 2, "08", null)))
        parcels.linkListing(saved.id!!, "P")
        rents.save(RentEstimate(saved.id!!, 1800.0, null, null, 2, null))

        worker.execute(AnalyzeListingTask(saved.id!!, profile.id!!))

        val analysis = analyses.findForListing(saved.id!!, profile.id!!)
        assertNotNull(analysis)
        assertEquals(1963.2, analysis.netMonthlyCost, 1.5)
        assertNotNull(listings.findById(saved.id!!)!!.lastAnalyzedAt)
    }

    @Test
    fun worker_marks_analyzed_even_when_listing_cannot_be_scored() {
        val profile = profiles.save(BuyerProfile.defaults())
        val saved = listings.upsertSeen(listing("no-rent"), t0)

        worker.execute(AnalyzeListingTask(saved.id!!, profile.id!!))

        assertNull(analyses.findForListing(saved.id!!, profile.id!!))
        assertNotNull(listings.findById(saved.id!!)!!.lastAnalyzedAt)
    }

    @Test
    fun finder_returns_stale_listings_only_and_nothing_without_profile() {
        assertEquals(emptyList(), finder.findRequested("data-analyzer"))

        val profile = profiles.save(BuyerProfile.defaults())
        val stale = listings.upsertSeen(listing("stale"), t0)
        val fresh = listings.upsertSeen(listing("fresh"), t0)
        listings.markAnalyzed(fresh.id!!, Instant.now().plusSeconds(60))

        val tasks = finder.findRequested("data-analyzer")

        assertEquals(listOf(AnalyzeListingTask(stale.id!!, profile.id!!)), tasks)
    }

    @Test
    fun worker_removes_stale_analysis_when_listing_no_longer_qualifies() {
        val profile = profiles.save(BuyerProfile.defaults())
        val saved = listings.upsertSeen(listing("a"), t0)
        rents.save(RentEstimate(saved.id!!, 1800.0, null, null, 2, null))
        worker.execute(AnalyzeListingTask(saved.id!!, profile.id!!))
        assertNotNull(analyses.findForListing(saved.id!!, profile.id!!))

        profiles.save(profile.copy(maxDriveMinutes = 5))   // Cocoa is ~23 min away: no longer qualifies
        worker.execute(AnalyzeListingTask(saved.id!!, profile.id!!))

        assertNull(analyses.findForListing(saved.id!!, profile.id!!))
        assertEquals(0, analyses.rankedForProfile(profile.id!!).size)
    }
}
