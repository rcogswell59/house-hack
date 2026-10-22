package io.initialcapacity.househack.parcels

import io.initialcapacity.househack.listings.AddressNormalizer
import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.listings.ParcelFactRepository
import io.initialcapacity.househack.profile.ProfileRepository
import io.initialcapacity.workflow.CollectorTask
import io.initialcapacity.workflow.Worker
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Two daily jobs behind one class, selected by [link] and [sweep]:
 * link — give every active listing without a parcel one via point lookup (fallback: normalized-address match);
 * sweep — tile the buyer's search radius and upsert every multi-family parcel so unit counts and values are on hand.
 * The pipeline runs link before rent estimates and the slow sweep last.
 */
class ImportParcelsWorker(
    private val source: ParcelSource,
    private val parcels: ParcelFactRepository,
    private val listings: ListingRepository,
    private val profiles: ProfileRepository,
    private val link: Boolean = true,
    private val sweep: Boolean = true,
    private val tileDegrees: Double = 0.1,
    private val radiusMiles: Double = 30.0,
    override val name: String = "import-parcels",
) : Worker<CollectorTask> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun execute(task: CollectorTask) {
        // Throwing (rather than returning) keeps the scheduler from marking today's run complete before a profile exists.
        val profile = profiles.findFirst() ?: throw IllegalStateException("no buyer profile yet; create one at /profile")

        if (link) linkListings()
        if (sweep) sweepRadius(profile.workplaceLat, profile.workplaceLng)
    }

    private fun linkListings() {
        var linked = 0
        for (listing in listings.findActive()) {
            val id = listing.id!!
            if (parcels.findForListing(id) != null) continue

            val lat = listing.lat
            val lng = listing.lng
            val byPoint = if (lat != null && lng != null) runBlocking { source.parcelAt(lat, lng) } else null
            val match = byPoint?.also { parcels.upsertAll(listOf(it)) }
                ?: parcels.findByAddressNorm(AddressNormalizer.normalize(listing.address))

            if (match != null) {
                parcels.linkListing(id, match.parcelId)
                linked++
            } else {
                logger.info("no parcel found for listing {} ({})", id, listing.address)
            }
        }
        logger.info("linked {} listings to parcels", linked)
    }

    private fun sweepRadius(lat: Double, lng: Double) {
        val tiles = Envelope.around(lat, lng, radiusMiles).tiles(tileDegrees)
        var imported = 0
        tiles.forEachIndexed { i, tile ->
            imported += fetchTile(tile, "${i + 1}/${tiles.size}", retry = true)
        }
        logger.info("sweep complete: {} parcel rows upserted across {} tiles", imported, tiles.size)
    }

    /** Dense tiles can exceed the server's time limit; one retry as four quadrants recovers most of them. */
    private fun fetchTile(tile: Envelope, label: String, retry: Boolean): Int =
        try {
            val facts = runBlocking { source.multiFamilyParcels(tile) }
            logger.info("tile {}: {} multi-family parcels", label, facts.size)
            parcels.upsertAll(facts)
        } catch (e: Exception) {
            if (retry) {
                logger.warn("tile {} failed ({}); retrying as four quadrants", label, e.message)
                tile.quadrants().withIndex().sumOf { (q, quadrant) -> fetchTile(quadrant, "$label.${q + 1}", retry = false) }
            } else {
                logger.warn("tile {} failed again: {}", label, e.message)
                0
            }
        }
}
