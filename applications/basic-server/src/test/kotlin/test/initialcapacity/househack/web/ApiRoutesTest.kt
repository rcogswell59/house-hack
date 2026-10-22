package test.initialcapacity.househack.web

import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiRoutesTest {
    @Before
    fun setUp() = TestDatabase.truncateAll()

    @Test
    fun api_listings_returns_json_sorted_by_net_cost() = WebTest.run { client ->
        val profile = WebTest.seedProfile()
        val pricey = WebTest.seedListing("pricey", "880 Barton Blvd", 849_000.0)
        val cheap = WebTest.seedListing("cheap", "215 Dixon Blvd")
        WebTest.seedAnalysis(pricey, profile, 3_100.0)
        WebTest.seedAnalysis(cheap, profile, 1_963.2)

        val response = client.get("/api/listings?sort=net_monthly_cost")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        val rows = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(2, rows.size)
        assertEquals("215 Dixon Blvd", rows[0].jsonObject["address"]!!.jsonPrimitive.content)
        assertTrue(rows[0].jsonObject["netMonthlyCost"]!!.jsonPrimitive.double <= rows[1].jsonObject["netMonthlyCost"]!!.jsonPrimitive.double)
        assertEquals(cheap.id, rows[0].jsonObject["listingId"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun api_listing_detail_returns_analysis_and_listing() = WebTest.run { client ->
        val profile = WebTest.seedProfile()
        val listing = WebTest.seedListing("cheap", "215 Dixon Blvd")
        WebTest.seedAnalysis(listing, profile, 1_963.2)

        val body = Json.parseToJsonElement(client.get("/api/listings/${listing.id}").bodyAsText()).jsonObject

        assertEquals("215 Dixon Blvd", body["listing"]!!.jsonObject["address"]!!.jsonPrimitive.content)
        assertEquals(1_963.2, body["analysis"]!!.jsonObject["netMonthlyCost"]!!.jsonPrimitive.double)
        assertTrue(body["analysis"]!!.jsonObject["breakdown"]!!.jsonObject.containsKey("buyer"))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/listings/424242").status)
    }

    @Test
    fun api_profile_get_and_put_roundtrip() = WebTest.run { client ->
        WebTest.seedProfile()
        val current = client.get("/api/profile").bodyAsText()
        assertEquals(118_000.0, Json.parseToJsonElement(current).jsonObject["annualIncome"]!!.jsonPrimitive.double)

        val updated = current.replace("118000.0", "130000.0")
        val response = client.put("/api/profile") { contentType(ContentType.Application.Json); setBody(updated) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(130_000.0, WebTest.deps.profiles.findFirst()!!.annualIncome)
    }

    @Test
    fun api_profile_put_validates() = WebTest.run { client ->
        WebTest.seedProfile()
        val current = Json.parseToJsonElement(client.get("/api/profile").bodyAsText()).jsonObject
        val bad = JsonObject(current + ("creditScore" to JsonPrimitive(900)))

        val response = client.put("/api/profile") { contentType(ContentType.Application.Json); setBody(bad.toString()) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errors = Json.parseToJsonElement(response.bodyAsText()).jsonObject["errors"]!!.jsonArray
        assertTrue(errors.any { it.jsonPrimitive.content.contains("Credit score") })
        assertEquals(720, WebTest.deps.profiles.findFirst()!!.creditScore)
    }

    @Test
    fun api_profile_get_404_when_none() = WebTest.run { client ->
        assertEquals(HttpStatusCode.NotFound, client.get("/api/profile").status)
    }
}
