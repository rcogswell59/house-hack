package test.initialcapacity.househack.collector

import io.initialcapacity.househack.collector.CollectListingsWorker
import io.initialcapacity.househack.db.DatabaseTemplate
import io.initialcapacity.househack.listings.ApiBudgetRepository
import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.profile.BuyerProfile
import io.initialcapacity.househack.profile.ProfileRepository
import io.initialcapacity.househack.rentcast.FakeRentCastClient
import io.initialcapacity.workflow.CollectorTask
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectListingsWorkerTest {
    private val db = DatabaseTemplate(TestDatabase.dataSource)
    private val listings = ListingRepository(db)
    private val budget = ApiBudgetRepository(db)
    private val profiles = ProfileRepository(db)
    private val client = FakeRentCastClient()
    private val day1 = Instant.parse("2026-10-21T06:00:00Z")
    private val month = LocalDate.of(2026, 10, 1)

    private fun worker(now: Instant) = CollectListingsWorker(client, listings, budget, profiles, Clock.fixed(now, ZoneOffset.UTC))
    private fun task(now: Instant) = CollectorTask("collect-listings", LocalDate.ofInstant(now, ZoneOffset.UTC))

    @Before
    fun setUp() {
        TestDatabase.truncateAll()
        profiles.save(BuyerProfile.defaults())
    }

    @Test
    fun collect_upserts_all_listings_from_client_and_reserves_one_call() {
        worker(day1).execute(task(day1))

        val active = listings.findActive()
        assertEquals(6, active.size)
        assertTrue(active.all { it.source == "rentcast" && it.active && it.raw.contains("formattedAddress") })
        assertEquals("215 Dixon Blvd", active.single { it.sourceId.startsWith("215-Dixon") }.address)
        assertEquals(1, budget.callsUsed("rentcast", month))
        assertEquals(listOf("searchSaleListings"), client.calls)
    }

    @Test
    fun collect_marks_missing_listings_inactive_after_grace_period() {
        worker(day1).execute(task(day1))
        client.listings = client.listings.drop(1)

        val day5 = day1.plusSeconds(4 * 86_400)
        worker(day5).execute(task(day5))

        val active = listings.findActive()
        assertEquals(5, active.size)
        assertEquals(6, db.queryOne("select count(*) from listings") { it.getInt(1) })
    }

    @Test
    fun collect_skips_when_budget_exhausted() {
        repeat(50) { budget.tryReserve("rentcast", month, 50) }

        worker(day1).execute(task(day1))

        assertTrue(client.calls.isEmpty())
        assertEquals(0, listings.findActive().size)
        assertEquals(50, budget.callsUsed("rentcast", month))
    }

    @Test
    fun collect_without_a_profile_throws_so_the_day_is_not_marked_done() {
        TestDatabase.truncateAll()

        val e = runCatching { worker(day1).execute(task(day1)) }.exceptionOrNull()

        assertTrue(e is IllegalStateException, "expected IllegalStateException, got $e")
        assertTrue(client.calls.isEmpty())
    }
}
