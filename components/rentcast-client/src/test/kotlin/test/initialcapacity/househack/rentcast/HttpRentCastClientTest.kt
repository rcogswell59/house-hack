package test.initialcapacity.househack.rentcast

import io.initialcapacity.househack.rentcast.FakeRentCastClient
import io.initialcapacity.househack.rentcast.HttpRentCastClient
import io.initialcapacity.househack.rentcast.RentCastException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpRentCastClientTest {
    private val listingsJson = """
        [
          {"id":"100-A-St,-Cocoa,-FL-32922","formattedAddress":"100 A St, Cocoa, FL 32922","addressLine1":"100 A St","city":"Cocoa","state":"FL",
           "zipCode":"32922","county":"Brevard","latitude":28.38,"longitude":-80.74,"propertyType":"Multi-Family","bedrooms":4,"bathrooms":2,
           "squareFootage":1900,"yearBuilt":1970,"status":"Active","price":425000,"listedDate":"2026-10-01T00:00:00.000Z","daysOnMarket":20,
           "listingAgent":{"name":"Someone"},"history":{"2026-10-01":{"event":"Sale Listing","price":425000}}},
          {"id":"200-B-St,-Rockledge,-FL-32955","formattedAddress":"200 B St, Rockledge, FL 32955","addressLine1":"200 B St","city":"Rockledge",
           "state":"FL","zipCode":"32955","latitude":28.33,"longitude":-80.72,"propertyType":"Multi-Family","bathrooms":3,"status":"Active","price":510000}
        ]
    """.trimIndent()

    private val rentJson = """{"rent":1620,"rentRangeLow":1550,"rentRangeHigh":1690,"subjectProperty":{"id":"x"},"comparables":[]}"""

    private val requests = mutableListOf<HttpRequestData>()

    private fun client(status: HttpStatusCode = HttpStatusCode.OK, body: (HttpRequestData) -> String) = HttpRentCastClient(
        apiKey = "k",
        engine = MockEngine { request ->
            requests += request
            respond(body(request), status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    @Test
    fun searchSaleListings_sends_key_and_params_and_parses() = runBlocking {
        val listings = client { listingsJson }.searchSaleListings(lat = 28.39, lng = -80.61, radiusMiles = 30.0)

        val request = requests.single()
        assertEquals("/v1/listings/sale", request.url.encodedPath)
        assertEquals("k", request.headers["X-Api-Key"])
        assertEquals("Multi-Family", request.url.parameters["propertyType"])
        assertEquals("Active", request.url.parameters["status"])
        assertEquals("30.0", request.url.parameters["radius"])
        assertEquals("500", request.url.parameters["limit"])

        assertEquals(2, listings.size)
        assertEquals(425000.0, listings[0].price)
        assertEquals(4, listings[0].bedrooms)
        assertEquals("Cocoa", listings[0].city)
        assertNull(listings[1].bedrooms)
        assertTrue(listings[0].raw.contains("\"mlsNumber\"").not() && listings[0].raw.contains("\"formattedAddress\""))
    }

    @Test
    fun rentEstimate_parses_rent_and_range_and_omits_null_params() = runBlocking {
        val estimate = client { rentJson }.rentEstimate(address = "100 A St, Cocoa, FL 32922", bedrooms = 2, bathrooms = null, sqft = null)

        val request = requests.single()
        assertEquals("/v1/avm/rent/long-term", request.url.encodedPath)
        assertEquals("100 A St, Cocoa, FL 32922", request.url.parameters["address"])
        assertEquals("2", request.url.parameters["bedrooms"])
        assertNull(request.url.parameters["bathrooms"])
        assertNull(request.url.parameters["squareFootage"])

        assertEquals(1620.0, estimate.rent)
        assertEquals(1550.0, estimate.rentRangeLow)
        assertEquals(1690.0, estimate.rentRangeHigh)
    }

    @Test
    fun non_2xx_throws_RentCastException() {
        val e = assertFailsWith<RentCastException> {
            runBlocking { client(HttpStatusCode.Unauthorized) { """{"status":401,"error":"auth/api-key-invalid"}""" }.rentEstimate("x", bedrooms = null, bathrooms = null, sqft = null) }
        }
        assertEquals(401, e.status)
        assertTrue(e.body.contains("api-key-invalid"))
    }

    @Test
    fun fake_client_ships_six_brevard_samples_and_records_calls() = runBlocking {
        val fake = FakeRentCastClient()

        val listings = fake.searchSaleListings(28.39, -80.61, 30.0)
        fake.rentEstimate("whatever", bedrooms = 2, bathrooms = null, sqft = null)

        assertEquals(6, listings.size)
        assertTrue(listings.all { it.propertyType == "Multi-Family" && it.price > 0 })
        assertEquals(1, listings.count { it.bedrooms == null })
        assertEquals(listOf("searchSaleListings", "rentEstimate(bedrooms=2)"), fake.calls)
    }
}
