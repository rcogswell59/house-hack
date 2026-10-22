package io.initialcapacity.househack.analyzer

import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.profile.ProfileRepository
import io.initialcapacity.workflow.WorkFinder
import org.slf4j.LoggerFactory
import java.time.Instant

/** The analyzer's queue is the database: active listings whose inputs changed since they were last analyzed. */
class StaleAnalysisWorkFinder(
    private val listings: ListingRepository,
    private val profiles: ProfileRepository,
) : WorkFinder<AnalyzeListingTask> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun findRequested(name: String): List<AnalyzeListingTask> {
        val profile = profiles.findFirst() ?: return emptyList()
        val profileId = profile.id ?: return emptyList()
        val stale = listings.findStaleForAnalysis(profile.updatedAt ?: Instant.EPOCH)
        if (stale.isNotEmpty()) logger.info("{} listings need analysis", stale.size)
        return stale.map { AnalyzeListingTask(it.id!!, profileId) }
    }

    override fun markCompleted(info: AnalyzeListingTask) {
        logger.debug("analysis task complete for listing {}", info.listingId)
    }
}
