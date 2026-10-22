package io.initialcapacity.househack.analyzer

import io.initialcapacity.househack.listings.AnalysisRepository
import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.listings.ParcelFactRepository
import io.initialcapacity.househack.listings.RentEstimateRepository
import io.initialcapacity.househack.profile.ProfileRepository
import io.initialcapacity.workflow.Worker
import org.slf4j.LoggerFactory
import java.time.Instant

class AnalyzeListingWorker(
    private val analyzer: ListingAnalyzer,
    private val listings: ListingRepository,
    private val parcels: ParcelFactRepository,
    private val rents: RentEstimateRepository,
    private val profiles: ProfileRepository,
    private val analyses: AnalysisRepository,
    override val name: String = "data-analyzer",
) : Worker<AnalyzeListingTask> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun execute(task: AnalyzeListingTask) {
        val listing = listings.findById(task.listingId) ?: return
        val profile = profiles.findFirst()?.takeIf { it.id == task.profileId } ?: return

        val listingId = listing.id!!
        val analysis = analyzer.analyze(profile, listing, parcels.findForListing(listingId), rents.findForListing(listingId))
        if (analysis != null) {
            analyses.upsert(analysis)
            logger.info("analyzed listing {}: net monthly cost {}", listingId, "%.0f".format(analysis.netMonthlyCost))
        } else if (analyses.deleteForListing(listingId, profile.id!!) > 0) {
            logger.info("listing {} no longer qualifies; removed from the ranking", listingId)
        }
        // Unscoreable listings leave the queue too; they re-enter when a rent estimate, parcel, or profile change arrives.
        listings.markAnalyzed(listingId, Instant.now())
    }
}
