package io.initialcapacity.househack.collector

import io.initialcapacity.househack.listings.ApiBudgetRepository
import io.initialcapacity.househack.listings.Listing
import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.listings.ParcelFactRepository
import io.initialcapacity.househack.listings.RentEstimate
import io.initialcapacity.househack.listings.RentEstimateRepository
import io.initialcapacity.househack.rentcast.RentCastClient
import io.initialcapacity.househack.rentcast.RentCastException
import io.initialcapacity.workflow.CollectorTask
import io.initialcapacity.workflow.Worker
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import kotlin.math.ceil

/**
 * Fetches a per-unit rent estimate for listings that have none, oldest first, a few per day.
 * [monthlyLimit] is below the plan limit so the daily listing search always has calls left.
 */
class FetchRentEstimatesWorker(
    private val client: RentCastClient,
    private val listings: ListingRepository,
    private val rents: RentEstimateRepository,
    private val parcels: ParcelFactRepository,
    private val budget: ApiBudgetRepository,
    private val clock: Clock,
    private val perRunCap: Int = 5,
    private val monthlyLimit: Int = 45,
    override val name: String = "fetch-rents",
) : Worker<CollectorTask> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun execute(task: CollectorTask) {
        val now = clock.instant()
        val month = LocalDate.ofInstant(now, clock.zone)

        for (listing in listings.findWithoutRentEstimate(perRunCap, retryFailedAfter = now)) {
            if (!budget.tryReserve(CollectListingsWorker.SOURCE, month, monthlyLimit)) {
                logger.warn("RentCast rent-estimate budget ({}) reached for the month; stopping", monthlyLimit)
                return
            }

            val listingId = listing.id!!
            val units = parcels.findForListing(listingId)?.units?.takeIf { it > 0 } ?: DEFAULT_UNITS
            val bedsPerUnit = listing.bedrooms?.let { ceil(it / units.toDouble()).toInt() }
            try {
                val estimate = runBlocking { client.rentEstimate(address = fullAddress(listing), bedrooms = bedsPerUnit, bathrooms = null, sqft = null) }
                rents.save(RentEstimate(listingId, estimate.rent, estimate.rentRangeLow, estimate.rentRangeHigh, bedsPerUnit, now))
                logger.info("rent estimate for listing {}: {}/unit", listingId, estimate.rent)
            } catch (e: RentCastException) {
                // One bad address (no AVM, 404) must not stop the others or be retried every run.
                listings.markRentEstimateFailed(listingId, now)
                logger.warn("rent estimate failed for listing {} ({}); parked for {} days", listingId, e.message, ListingRepository.RETRY_FAILED_AFTER_DAYS)
            }
        }
    }

    private fun fullAddress(l: Listing) = listOfNotNull(l.address, l.city, l.zip?.let { "FL $it" }).joinToString(", ")

    companion object {
        /** Without parcel data a multi-family listing is assumed to be a duplex. */
        const val DEFAULT_UNITS = 2
    }
}
