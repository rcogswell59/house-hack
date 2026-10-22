package io.initialcapacity.workflow

import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Offers each worker exactly one task per calendar day (UTC). State is in memory: a restart re-runs today's
 * jobs once, which is harmless because every daily job upserts and reserves its API budget first.
 */
class DailyWorkFinder(private val clock: Clock) : WorkFinder<CollectorTask> {
    private val lastCompleted = ConcurrentHashMap<String, LocalDate>()

    override fun findRequested(name: String): List<CollectorTask> {
        val today = LocalDate.ofInstant(clock.instant(), clock.zone)
        return if (lastCompleted[name] == today) emptyList() else listOf(CollectorTask(name, today))
    }

    override fun markCompleted(info: CollectorTask) {
        lastCompleted[info.name] = info.runDate
    }
}
