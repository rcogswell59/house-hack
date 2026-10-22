package test.initialcapacity.workflow

import io.initialcapacity.workflow.CollectorTask
import io.initialcapacity.workflow.DailyWorkFinder
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals

class DailyWorkFinderTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = now
    }

    @Test
    fun daily_finder_returns_one_task_per_day_per_worker() {
        val clock = MutableClock(Instant.parse("2026-10-21T08:00:00Z"))
        val finder = DailyWorkFinder(clock)

        val first = finder.findRequested("collect-listings")
        assertEquals(listOf(CollectorTask("collect-listings", LocalDate.of(2026, 10, 21))), first)
        finder.markCompleted(first.single())

        assertEquals(emptyList(), finder.findRequested("collect-listings"))
        assertEquals(1, finder.findRequested("fetch-rents").size)   // other workers are independent

        clock.now = Instant.parse("2026-10-22T00:30:00Z")
        assertEquals(LocalDate.of(2026, 10, 22), finder.findRequested("collect-listings").single().runDate)
    }

    @Test
    fun unfinished_task_is_offered_again() {
        val finder = DailyWorkFinder(Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneOffset.UTC))

        finder.findRequested("collect-listings")

        assertEquals(1, finder.findRequested("collect-listings").size)
    }
}
