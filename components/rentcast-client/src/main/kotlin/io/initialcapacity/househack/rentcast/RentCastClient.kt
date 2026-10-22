package io.initialcapacity.househack.rentcast

interface RentCastClient {
    suspend fun searchSaleListings(
        lat: Double,
        lng: Double,
        radiusMiles: Double,
        propertyType: String = "Multi-Family",
        limit: Int = 500,
    ): List<SaleListingDto>

    suspend fun rentEstimate(
        address: String,
        propertyType: String = "Multi-Family",
        bedrooms: Int?,
        bathrooms: Double?,
        sqft: Int?,
    ): RentEstimateDto
}

class RentCastException(val status: Int, val body: String) : RuntimeException("RentCast returned $status: $body")
