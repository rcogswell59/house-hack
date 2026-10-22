package io.initialcapacity.househack.collector

import io.initialcapacity.househack.listings.ApiBudgetRepository
import io.initialcapacity.househack.listings.Listing
import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.profile.ProfileRepository
import io.initialcapacity.househack.rentcast.RentCastClient
import io.initialcapacity.househack.rentcast.SaleListingDto
import io.initialcapacity.workflow.CollectorTask
import io.initialcapacity.workflow.Worker
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Once a day: one RentCast search centred on the buyer's workplace, upserted into `listings`. */
class CollectListingsWorker(
    private val client: RentCastClient,
    private val listings: ListingRepository,
    private val budget: ApiBudgetRepository,
    private val profiles: ProfileRepository,
    private val clock: Clock,
    private val radiusMiles: Double = 30.0,
    private val monthlyLimit: Int = 50,
    override val name: String = "collect-listings",
) : Worker<CollectorTask> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun execute(task: CollectorTask) {
        // Throwing (rather than returning) keeps the scheduler from marking today's run complete before a profile exists.
        val profile = profiles.findFirst() ?: throw IllegalStateException("no buyer profile yet; create one at /profile")
        val now = clock.instant()

        if (!budget.tryReserve(SOURCE, LocalDate.ofInstant(now, clock.zone), monthlyLimit)) {
            logger.warn("RentCast monthly budget ({}) exhausted; skipping listing collection", monthlyLimit)
            return
        }

        val found = runBlocking { client.searchSaleListings(profile.workplaceLat, profile.workplaceLng, radiusMiles) }
        found.forEach { listings.upsertSeen(toListing(it), now) }
        val retired = listings.markInactiveNotSeenSince(SOURCE, now.minus(GRACE))

        logger.info("collected {} listings, retired {}", found.size, retired)
    }

    companion object {
        const val SOURCE = "rentcast"
        private val GRACE: Duration = Duration.ofDays(3)

        fun toListing(dto: SaleListingDto) = Listing(
            id = null,
            source = SOURCE,
            sourceId = dto.id,
            address = dto.addressLine1 ?: dto.formattedAddress.substringBefore(","),
            city = dto.city,
            zip = dto.zipCode,
            lat = dto.latitude,
            lng = dto.longitude,
            price = dto.price,
            bedrooms = dto.bedrooms,
            bathrooms = dto.bathrooms,
            sqft = dto.squareFootage,
            yearBuilt = dto.yearBuilt,
            propertyType = dto.propertyType,
            listedAt = dto.listedDate?.let { runCatching { Instant.parse(it) }.getOrNull() },
            firstSeen = null,
            lastSeen = null,
            active = true,
            lastAnalyzedAt = null,
            raw = dto.raw.ifEmpty { "{}" },
        )
    }
}
