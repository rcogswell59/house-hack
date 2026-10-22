package io.initialcapacity.househack.rentcast

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Subset of RentCast's sale-listing record; [raw] keeps the full JSON element for storage. */
@Serializable
data class SaleListingDto(
    val id: String,
    val formattedAddress: String,
    val addressLine1: String? = null,
    val city: String? = null,
    val zipCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val propertyType: String? = null,
    val bedrooms: Int? = null,
    val bathrooms: Double? = null,
    val squareFootage: Int? = null,
    val yearBuilt: Int? = null,
    val price: Double,
    val status: String? = null,
    val listedDate: String? = null,
    @Transient val raw: String = "",
)

@Serializable
data class RentEstimateDto(
    val rent: Double,
    val rentRangeLow: Double? = null,
    val rentRangeHigh: Double? = null,
)
