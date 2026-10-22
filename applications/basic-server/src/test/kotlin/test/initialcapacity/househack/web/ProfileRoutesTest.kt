package test.initialcapacity.househack.web

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileRoutesTest {
    @Before
    fun setUp() = TestDatabase.truncateAll()

    private fun defaultsForm(vararg overrides: Pair<String, String>) = parameters {
        append("name", "Ryan"); append("annualIncome", "118000"); append("liquidSavings", "20000"); append("creditScore", "720")
        append("firstTimeBuyer", "on"); append("householdSize", "1"); append("otherMonthlyDebts", "0")
        append("workplaceLat", "28.3922"); append("workplaceLng", "-80.6077"); append("maxDriveMinutes", "30")
        append("unitsMin", "2"); append("unitsMax", "4"); append("vacancyRate", "0.08"); append("maintenanceRate", "0.05")
        append("insuranceRate", "0.012"); append("closingCostRate", "0.03"); append("mortgageRate", "0.065")
        append("roadFactor", "1.4"); append("avgSpeedMph", "30")
        overrides.forEach { (k, v) -> remove(k); append(k, v) }
    }

    @Test
    fun profile_get_shows_defaults_when_empty() = WebTest.run { client ->
        val response = client.get("/profile")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "name=\"annualIncome\"")
        assertContains(body, "118000")
        assertContains(body, "name=\"maxDriveMinutes\"")
    }

    @Test
    fun profile_post_saves_and_redirects() = WebTest.run { _ ->
        val noRedirect = createClient { followRedirects = false }

        val response = noRedirect.submitForm("/profile", defaultsForm("annualIncome" to "125000", "veteran" to "on"))

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/report", response.headers["Location"])
        val saved = WebTest.deps.profiles.findFirst()!!
        assertEquals(125_000.0, saved.annualIncome)
        assertEquals(true, saved.veteran)
        assertEquals(false, saved.hthEligibleOccupation)
    }

    @Test
    fun profile_post_updates_existing_row_instead_of_inserting() = WebTest.run { _ ->
        WebTest.seedProfile()
        val noRedirect = createClient { followRedirects = false }

        noRedirect.submitForm("/profile", defaultsForm("maxDriveMinutes" to "45"))

        assertEquals(45, WebTest.deps.profiles.findFirst()!!.maxDriveMinutes)
        assertEquals(1, WebTest.deps.db.queryOne("select count(*) from profiles") { it.getInt(1) })
    }

    @Test
    fun profile_post_rejects_invalid_values() = WebTest.run { client ->
        val response = client.submitForm("/profile", defaultsForm("creditScore" to "900", "annualIncome" to "-5"))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Credit score")
        assertContains(body, "income")
        assertContains(body, "value=\"900\"")   // form re-rendered with what the user typed
        assertNull(WebTest.deps.profiles.findFirst())
    }

    @Test
    fun profile_post_rejects_non_numeric_input() = WebTest.run { client ->
        val response = client.submitForm("/profile", defaultsForm("annualIncome" to "lots"))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "number")
        assertNull(WebTest.deps.profiles.findFirst())
    }
}
