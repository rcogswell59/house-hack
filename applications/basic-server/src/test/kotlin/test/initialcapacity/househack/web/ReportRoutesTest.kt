package test.initialcapacity.househack.web

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportRoutesTest {
    @Before
    fun setUp() = TestDatabase.truncateAll()

    @Test
    fun root_redirects_to_report() = WebTest.run { _ ->
        val response = createClient { followRedirects = false }.get("/")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/report", response.headers["Location"])
    }

    @Test
    fun report_without_profile_points_to_profile_form() = WebTest.run { client ->
        val response = client.get("/report")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "/profile")
    }

    @Test
    fun report_lists_ranked_rows_and_pending_count() = WebTest.run { client ->
        val profile = WebTest.seedProfile()
        val pricey = WebTest.seedListing("pricey", "880 Barton Blvd", 849_000.0)
        val cheap = WebTest.seedListing("cheap", "215 Dixon Blvd", 400_000.0)
        WebTest.seedListing("pending", "45 Grant Ave")
        WebTest.seedAnalysis(pricey, profile, 3_100.0, lenderPass = false)
        WebTest.seedAnalysis(cheap, profile, 1_963.2)

        val body = client.get("/report").bodyAsText()

        assertTrue(body.indexOf("215 Dixon Blvd") < body.indexOf("880 Barton Blvd"), "cheapest first")
        assertContains(body, "1,963")
        assertContains(body, "1 pending")
        assertContains(body, "/listings/${cheap.id}")
        assertTrue(!body.contains("45 Grant Ave"), "unanalyzed listing is not ranked")
    }

    @Test
    fun listing_detail_shows_buyer_and_lender_sections() = WebTest.run { client ->
        val profile = WebTest.seedProfile()
        val listing = WebTest.seedListing("cheap", "215 Dixon Blvd")
        WebTest.seedAnalysis(listing, profile, 1_963.2)

        val response = client.get("/listings/${listing.id}")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "215 Dixon Blvd")
        assertContains(body, "Buyer view")
        assertContains(body, "Lender view")
        assertContains(body, "BREVARD_HFA")
        assertContains(body, "2,482")   // principal & interest from the breakdown
    }

    @Test
    fun listing_detail_404_for_unknown_id() = WebTest.run { client ->
        assertEquals(HttpStatusCode.NotFound, client.get("/listings/999999").status)
    }
}
