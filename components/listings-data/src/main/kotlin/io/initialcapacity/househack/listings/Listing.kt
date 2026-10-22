package io.initialcapacity.househack.listings

import java.time.Instant

data class Listing(
    val id: Long?,
    val source: String,
    val sourceId: String,
    val address: String,
    val city: String?,
    val zip: String?,
    val lat: Double?,
    val lng: Double?,
    val price: Double,
    val bedrooms: Int?,
    val bathrooms: Double?,
    val sqft: Int?,
    val yearBuilt: Int?,
    val propertyType: String?,
    val listedAt: Instant?,
    val firstSeen: Instant?,
    val lastSeen: Instant?,
    val active: Boolean = true,
    val lastAnalyzedAt: Instant?,
    val raw: String,
)
