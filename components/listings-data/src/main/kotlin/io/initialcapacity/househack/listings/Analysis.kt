package io.initialcapacity.househack.listings

import io.initialcapacity.househack.db.DatabaseTemplate
import java.sql.ResultSet
import java.time.Instant

data class Analysis(
    val id: Long?,
    val listingId: Long,
    val profileId: Long,
    val runAt: Instant?,
    val netMonthlyCost: Double,
    val cashToClose: Double,
    val lenderPass: Boolean,
    val selfSufficiencyRatio: Double?,
    val dti: Double,
    val driveMinutes: Double,
    val breakdownJson: String,
    val programResultsJson: String,
)

class AnalysisRepository(private val db: DatabaseTemplate) {

    fun upsert(a: Analysis): Analysis = db.queryOne(
        """
        insert into analyses (listing_id, profile_id, run_at, net_monthly_cost, cash_to_close, lender_pass, self_sufficiency_ratio, dti, drive_minutes, breakdown, program_results)
        values (?, ?, now(), ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
        on conflict (listing_id, profile_id) do update set run_at = now(), net_monthly_cost = excluded.net_monthly_cost,
          cash_to_close = excluded.cash_to_close, lender_pass = excluded.lender_pass, self_sufficiency_ratio = excluded.self_sufficiency_ratio,
          dti = excluded.dti, drive_minutes = excluded.drive_minutes, breakdown = excluded.breakdown, program_results = excluded.program_results
        returning *
        """,
        a.listingId, a.profileId, a.netMonthlyCost, a.cashToClose, a.lenderPass, a.selfSufficiencyRatio, a.dti, a.driveMinutes,
        a.breakdownJson, a.programResultsJson,
        mapper = ::map,
    )!!

    fun findForListing(listingId: Long, profileId: Long): Analysis? =
        db.queryOne("select * from analyses where listing_id = ? and profile_id = ?", listingId, profileId, mapper = ::map)

    /** Removes a listing from the ranking when it no longer qualifies for this profile. */
    fun deleteForListing(listingId: Long, profileId: Long): Int =
        db.update("delete from analyses where listing_id = ? and profile_id = ?", listingId, profileId)

    /** Active listings with an analysis for this profile, cheapest net monthly cost first. */
    fun rankedForProfile(profileId: Long): List<Pair<Analysis, Listing>> = db.query(
        """
        select a.*, l.id as l_id, l.* from analyses a join listings l on l.id = a.listing_id
        where a.profile_id = ? and l.active order by a.net_monthly_cost asc, a.listing_id
        """,
        profileId,
    ) { rs -> map(rs) to ListingRepository.map(rs).copy(id = rs.getLong("l_id")) }

    fun countPendingForProfile(profileId: Long): Int = db.queryOne(
        "select count(*) from listings l left join analyses a on a.listing_id = l.id and a.profile_id = ? where l.active and a.id is null",
        profileId,
    ) { it.getInt(1) }!!

    private fun map(rs: ResultSet) = Analysis(
        id = rs.getLong("id"),
        listingId = rs.getLong("listing_id"),
        profileId = rs.getLong("profile_id"),
        runAt = rs.getTimestamp("run_at")?.toInstant(),
        netMonthlyCost = rs.getDouble("net_monthly_cost"),
        cashToClose = rs.getDouble("cash_to_close"),
        lenderPass = rs.getBoolean("lender_pass"),
        selfSufficiencyRatio = rs.getBigDecimal("self_sufficiency_ratio")?.toDouble(),
        dti = rs.getDouble("dti"),
        driveMinutes = rs.getDouble("drive_minutes"),
        breakdownJson = rs.getString("breakdown"),
        programResultsJson = rs.getString("program_results"),
    )
}
