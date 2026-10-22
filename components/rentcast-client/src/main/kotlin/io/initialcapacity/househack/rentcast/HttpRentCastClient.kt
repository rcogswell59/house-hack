package io.initialcapacity.househack.rentcast

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject

class HttpRentCastClient(
    private val apiKey: String,
    engine: HttpClientEngine = CIO.create(),
    private val baseUrl: String = "https://api.rentcast.io/v1",
) : RentCastClient {

    private val http = HttpClient(engine) { expectSuccess = false }

    override suspend fun searchSaleListings(lat: Double, lng: Double, radiusMiles: Double, propertyType: String, limit: Int): List<SaleListingDto> {
        val body = get("/listings/sale") {
            parameter("latitude", lat)
            parameter("longitude", lng)
            parameter("radius", radiusMiles)
            parameter("propertyType", propertyType)
            parameter("status", "Active")
            parameter("limit", limit)
        }
        return parseListings(body)
    }

    override suspend fun rentEstimate(address: String, propertyType: String, bedrooms: Int?, bathrooms: Double?, sqft: Int?): RentEstimateDto {
        val body = get("/avm/rent/long-term") {
            parameter("address", address)
            parameter("propertyType", propertyType)
            bedrooms?.let { parameter("bedrooms", it) }
            bathrooms?.let { parameter("bathrooms", it) }
            sqft?.let { parameter("squareFootage", it) }
        }
        return json.decodeFromString(RentEstimateDto.serializer(), body)
    }

    private suspend fun get(path: String, configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String {
        val response: HttpResponse = http.get(baseUrl + path) {
            header("X-Api-Key", apiKey)
            header("Accept", "application/json")
            configure()
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) throw RentCastException(response.status.value, text)
        return text
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Decodes each element and keeps its own JSON text as [SaleListingDto.raw]. */
        fun parseListings(body: String): List<SaleListingDto> =
            (json.parseToJsonElement(body) as JsonArray).map { element ->
                json.decodeFromJsonElement(SaleListingDto.serializer(), element.jsonObject).copy(raw = element.toString())
            }
    }
}
