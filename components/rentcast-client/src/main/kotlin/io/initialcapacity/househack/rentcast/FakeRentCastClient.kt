package io.initialcapacity.househack.rentcast

/** Test double and no-API-key fallback: canned Brevard listings, one rent figure, and a call log. */
class FakeRentCastClient(
    var listings: List<SaleListingDto> = sampleListings(),
    var rent: RentEstimateDto = RentEstimateDto(1800.0, 1700.0, 1900.0),
) : RentCastClient {

    val calls = mutableListOf<String>()

    override suspend fun searchSaleListings(lat: Double, lng: Double, radiusMiles: Double, propertyType: String, limit: Int): List<SaleListingDto> {
        calls += "searchSaleListings"
        return listings
    }

    override suspend fun rentEstimate(address: String, propertyType: String, bedrooms: Int?, bathrooms: Double?, sqft: Int?): RentEstimateDto {
        calls += "rentEstimate(bedrooms=$bedrooms)"
        return rent
    }

    companion object {
        fun sampleListings(): List<SaleListingDto> {
            val body = FakeRentCastClient::class.java.getResourceAsStream("/rentcast/sample-sale-listings.json")!!.bufferedReader().readText()
            return HttpRentCastClient.parseListings(body)
        }
    }
}
