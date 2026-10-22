package io.initialcapacity.workflow

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Offers each worker exactly one task per calendar day (UTC). An offered task is leased for [leaseMinutes]
 * so a long run is not offered again while in flight; a run that throws is offered again once the lease lapses.
 * State is in memory: a restart re-runs today's jobs once, which is harmless because they upsert and budget first.
 */
class DailyWorkFinder(private val clock: Clock, private val leaseMinutes: Long = 6 * 60) : WorkFinder<CollectorTask> {
    private val lastCompleted = ConcurrentHashMap<String, LocalDate>()
    private val offeredAt = ConcurrentHashMap<String, Instant>()

    override fun findRequested(name: String): List<CollectorTask> {
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, clock.zone)
        if (lastCompleted[name] == today) return emptyList()

        val leaseEnd = offeredAt[name]?.plus(Duration.ofMinutes(leaseMinutes))
        if (leaseEnd != null && now.isBefore(leaseEnd)) return emptyList()

        offeredAt[name] = now
        return listOf(CollectorTask(name, today))
    }

    override fun markCompleted(info: CollectorTask) {
        lastCompleted[info.name] = info.runDate
        offeredAt.remove(info.name)
    }
}
