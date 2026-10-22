package test.initialcapacity.househack.collector

import io.initialcapacity.househack.collector.module
import io.initialcapacity.househack.wiring.Dependencies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AppTest {
    @Test
    fun health_is_ok_without_starting_workers() = testApplication {
        application { module(Dependencies(TestDatabase.dataSource), startWorkers = false) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"status\": \"ok\"")
    }
}
