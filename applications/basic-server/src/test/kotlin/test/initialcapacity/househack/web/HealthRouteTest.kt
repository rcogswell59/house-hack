package test.initialcapacity.househack.web

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import kotlin.test.assertEquals

class HealthRouteTest {
    @Before
    fun setUp() = TestDatabase.truncateAll()

    @Test
    fun health_reports_ok_and_counts() = WebTest.run { client ->
        val profile = WebTest.seedProfile()
        val done = WebTest.seedListing("done", "215 Dixon Blvd")
        WebTest.seedListing("pending", "45 Grant Ave")
        WebTest.seedAnalysis(done, profile, 1_963.2)

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("ok", body["status"]!!.jsonPrimitive.content)
        assertEquals("ok", body["db"]!!.jsonPrimitive.content)
        assertEquals(2, body["activeListings"]!!.jsonPrimitive.int)
        assertEquals(1, body["pendingListings"]!!.jsonPrimitive.int)
    }
}
