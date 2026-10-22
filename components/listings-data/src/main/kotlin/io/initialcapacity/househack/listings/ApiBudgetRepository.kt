package io.initialcapacity.househack.listings

import io.initialcapacity.househack.db.DatabaseTemplate
import java.time.LocalDate

/** Tracks external API calls per calendar month so the collector never exceeds the plan limit. */
class ApiBudgetRepository(private val db: DatabaseTemplate) {

    fun callsUsed(source: String, month: LocalDate): Int =
        db.queryOne("select calls_used from api_budget where source = ? and month = ?", source, month.withDayOfMonth(1)) { it.getInt(1) } ?: 0

    /** Atomically reserves [n] calls; returns false (and reserves nothing) if that would exceed [limit]. */
    fun tryReserve(source: String, month: LocalDate, limit: Int, n: Int = 1): Boolean {
        val updated = db.update(
            """
            insert into api_budget (source, month, calls_used) values (?, ?, ?)
            on conflict (source, month) do update set calls_used = api_budget.calls_used + excluded.calls_used
            where api_budget.calls_used + excluded.calls_used <= ?
            """,
            source, month.withDayOfMonth(1), n, limit,
        )
        return updated == 1 && n <= limit
    }
}
