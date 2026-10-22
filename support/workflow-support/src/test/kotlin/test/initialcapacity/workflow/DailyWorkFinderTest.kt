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
    fun unfinished_task_is_offered_again_once_its_lease_lapses() {
        val finder = DailyWorkFinder(Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneOffset.UTC), leaseMinutes = 0)

        finder.findRequested("collect-listings")

        assertEquals(1, finder.findRequested("collect-listings").size)
    }
}

class DailyWorkFinderLeaseTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = now
    }

    @Test
    fun offered_task_is_not_offered_again_while_its_lease_is_live() {
        val clock = MutableClock(Instant.parse("2026-10-21T08:00:00Z"))
        val finder = DailyWorkFinder(clock, leaseMinutes = 60)

        assertEquals(1, finder.findRequested("daily-pipeline").size)
        clock.now = clock.now.plusSeconds(5 * 60)
        assertEquals(emptyList(), finder.findRequested("daily-pipeline"))
    }

    @Test
    fun failed_task_is_offered_again_after_the_lease_expires() {
        val clock = MutableClock(Instant.parse("2026-10-21T08:00:00Z"))
        val finder = DailyWorkFinder(clock, leaseMinutes = 60)

        finder.findRequested("daily-pipeline")   // never marked completed (the run threw)
        clock.now = clock.now.plusSeconds(61 * 60)

        assertEquals(1, finder.findRequested("daily-pipeline").size)
    }

    @Test
    fun default_lease_outlasts_a_long_sweep() {
        val clock = MutableClock(Instant.parse("2026-10-21T08:00:00Z"))
        val finder = DailyWorkFinder(clock)

        finder.findRequested("daily-pipeline")
        clock.now = clock.now.plusSeconds(5 * 3600)

        assertEquals(emptyList(), finder.findRequested("daily-pipeline"))
    }
}
