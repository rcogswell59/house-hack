package io.initialcapacity.househack.parcels

import io.initialcapacity.househack.listings.ParcelFact

/** A lat/lng bounding box in WGS84. */
data class Envelope(val minLng: Double, val minLat: Double, val maxLng: Double, val maxLat: Double) {

    /** Splits the envelope into a grid of tiles at most [stepDegrees] on a side, west→east then south→north. */
    fun tiles(stepDegrees: Double): List<Envelope> {
        val result = mutableListOf<Envelope>()
        var lat = minLat
        while (lat < maxLat - EPSILON) {
            var lng = minLng
            while (lng < maxLng - EPSILON) {
                result += Envelope(lng, lat, round(minOf(lng + stepDegrees, maxLng)), round(minOf(lat + stepDegrees, maxLat)))
                lng = round(lng + stepDegrees)
            }
            lat = round(lat + stepDegrees)
        }
        return result
    }

    /** The four equal quadrants of this envelope, for retrying a tile the server refused. */
    fun quadrants(): List<Envelope> {
        val midLng = round((minLng + maxLng) / 2)
        val midLat = round((minLat + maxLat) / 2)
        return listOf(
            Envelope(minLng, minLat, midLng, midLat), Envelope(midLng, minLat, maxLng, midLat),
            Envelope(minLng, midLat, midLng, maxLat), Envelope(midLng, midLat, maxLng, maxLat),
        )
    }

    companion object {
        private const val EPSILON = 1e-9

        /** Keeps tile edges free of binary floating-point drift (28.400000000000002). */
        private fun round(v: Double) = Math.round(v * 1e9) / 1e9
        private const val MILES_PER_DEGREE_LAT = 69.0

        fun around(lat: Double, lng: Double, radiusMiles: Double): Envelope {
            val dLat = radiusMiles / MILES_PER_DEGREE_LAT
            val dLng = radiusMiles / (MILES_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)))
            return Envelope(lng - dLng, lat - dLat, lng + dLng, lat + dLat)
        }
    }
}

interface ParcelSource {
    /** Multi-family (2–9 unit) parcels intersecting [envelope]. */
    suspend fun multiFamilyParcels(envelope: Envelope): List<ParcelFact>

    /** The parcel containing the point, or null. */
    suspend fun parcelAt(lat: Double, lng: Double): ParcelFact?
}

/** Test double: canned answers keyed by point, plus one list for every tile sweep; records what was asked. */
class FakeParcelSource : ParcelSource {
    val byPoint = mutableMapOf<Pair<Double, Double>, ParcelFact>()
    var sweepResults: List<ParcelFact> = emptyList()
    var failWhen: (Envelope) -> Boolean = { false }
    var failPoints: Set<Pair<Double, Double>> = emptySet()
    val pointLookups = mutableListOf<Pair<Double, Double>>()
    val sweptTiles = mutableListOf<Envelope>()

    override suspend fun multiFamilyParcels(envelope: Envelope): List<ParcelFact> {
        sweptTiles += envelope
        if (failWhen(envelope)) throw IllegalStateException("ArcGIS error: simulated failure for $envelope")
        return sweepResults
    }

    override suspend fun parcelAt(lat: Double, lng: Double): ParcelFact? {
        pointLookups += lat to lng
        if ((lat to lng) in failPoints) throw IllegalStateException("ArcGIS error: simulated failure at $lat,$lng")
        return byPoint[lat to lng]
    }
}
