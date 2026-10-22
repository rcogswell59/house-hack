package io.initialcapacity.househack.listings

import io.initialcapacity.househack.db.DatabaseTemplate
import java.sql.ResultSet
import java.time.Instant

/** RentCast's AVM returns a single-unit figure for multi-family, so one estimate per listing is the per-unit rent. */
data class RentEstimate(
    val listingId: Long,
    val estRentPerUnit: Double,
    val low: Double?,
    val high: Double?,
    val bedsAssumed: Int?,
    val fetchedAt: Instant?,
)

class RentEstimateRepository(private val db: DatabaseTemplate) {

    fun save(r: RentEstimate) {
        db.update(
            """
            insert into rent_estimates (listing_id, est_rent_per_unit, low, high, beds_assumed, fetched_at)
            values (?, ?, ?, ?, ?, coalesce(?, now()))
            on conflict (listing_id) do update set est_rent_per_unit = excluded.est_rent_per_unit, low = excluded.low,
              high = excluded.high, beds_assumed = excluded.beds_assumed, fetched_at = excluded.fetched_at
            """,
            r.listingId, r.estRentPerUnit, r.low, r.high, r.bedsAssumed, r.fetchedAt,
        )
    }

    fun findForListing(listingId: Long): RentEstimate? =
        db.queryOne("select * from rent_estimates where listing_id = ?", listingId, mapper = ::map)

    private fun map(rs: ResultSet) = RentEstimate(
        listingId = rs.getLong("listing_id"),
        estRentPerUnit = rs.getDouble("est_rent_per_unit"),
        low = rs.getBigDecimal("low")?.toDouble(),
        high = rs.getBigDecimal("high")?.toDouble(),
        bedsAssumed = rs.getObject("beds_assumed") as Int?,
        fetchedAt = rs.getTimestamp("fetched_at")?.toInstant(),
    )
}
