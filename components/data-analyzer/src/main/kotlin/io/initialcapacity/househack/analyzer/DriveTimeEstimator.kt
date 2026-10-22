package io.initialcapacity.househack.analyzer

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** v1 estimate: straight-line distance scaled by a road factor at an average speed. Labeled as an estimate in the UI. */
object DriveTimeEstimator {
    private const val EARTH_RADIUS_MILES = 3958.8

    fun haversineMiles(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_MILES * asin(sqrt(a))
    }

    fun minutes(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, roadFactor: Double, avgSpeedMph: Double): Double =
        haversineMiles(fromLat, fromLng, toLat, toLng) * roadFactor / avgSpeedMph * 60
}
