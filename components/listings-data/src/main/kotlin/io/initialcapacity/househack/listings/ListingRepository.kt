package io.initialcapacity.househack.listings

import io.initialcapacity.househack.db.DatabaseTemplate
import java.sql.ResultSet
import java.time.Instant

class ListingRepository(private val db: DatabaseTemplate) {

    fun upsertSeen(l: Listing, seenAt: Instant): Listing = db.queryOne(
        """
        insert into listings (source, source_id, address, city, zip, lat, lng, price, bedrooms, bathrooms, sqft, year_built,
                              property_type, listed_at, first_seen, last_seen, active, raw)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?::jsonb)
        on conflict (source, source_id) do update set
          address = excluded.address, city = excluded.city, zip = excluded.zip, lat = excluded.lat, lng = excluded.lng,
          price = excluded.price, bedrooms = excluded.bedrooms, bathrooms = excluded.bathrooms, sqft = excluded.sqft,
          year_built = excluded.year_built, property_type = excluded.property_type, listed_at = excluded.listed_at,
          last_seen = excluded.last_seen, active = true, raw = excluded.raw
        returning *
        """,
        l.source, l.sourceId, l.address, l.city, l.zip, l.lat, l.lng, l.price, l.bedrooms, l.bathrooms, l.sqft, l.yearBuilt,
        l.propertyType, l.listedAt, seenAt, seenAt, l.raw,
        mapper = ::map,
    )!!

    fun markInactiveNotSeenSince(source: String, cutoff: Instant): Int =
        db.update("update listings set active = false where source = ? and active and last_seen < ?", source, cutoff)

    fun findById(id: Long): Listing? = db.queryOne("select * from listings where id = ?", id, mapper = ::map)

    fun findActive(): List<Listing> = db.query("select * from listings where active order by id", mapper = ::map)

    fun findWithoutRentEstimate(limit: Int): List<Listing> = db.query(
        "select l.* from listings l left join rent_estimates r on r.listing_id = l.id where l.active and r.listing_id is null order by l.first_seen, l.id limit ?",
        limit, mapper = ::map,
    )

    /** Active listings never analyzed, or analyzed before any of their inputs (listing, rent, parcel, profile) last changed. */
    fun findStaleForAnalysis(profileUpdatedAt: Instant): List<Listing> = db.query(
        """
        select l.* from listings l
          left join rent_estimates r on r.listing_id = l.id
          left join listing_parcel lp on lp.listing_id = l.id
          left join parcel_facts p on p.parcel_id = lp.parcel_id
        where l.active and (
          l.last_analyzed_at is null
          or l.last_analyzed_at < greatest(l.last_seen, coalesce(r.fetched_at, l.last_seen), coalesce(p.imported_at, l.last_seen), ?)
        )
        order by l.id
        """,
        profileUpdatedAt, mapper = ::map,
    )

    fun markAnalyzed(id: Long, at: Instant) {
        db.update("update listings set last_analyzed_at = ? where id = ?", at, id)
    }

    companion object {
        fun map(rs: ResultSet) = Listing(
            id = rs.getLong("id"),
            source = rs.getString("source"),
            sourceId = rs.getString("source_id"),
            address = rs.getString("address"),
            city = rs.getString("city"),
            zip = rs.getString("zip"),
            lat = rs.getObject("lat") as Double?,
            lng = rs.getObject("lng") as Double?,
            price = rs.getDouble("price"),
            bedrooms = rs.getObject("bedrooms") as Int?,
            bathrooms = rs.getBigDecimal("bathrooms")?.toDouble(),
            sqft = rs.getObject("sqft") as Int?,
            yearBuilt = rs.getObject("year_built") as Int?,
            propertyType = rs.getString("property_type"),
            listedAt = rs.getTimestamp("listed_at")?.toInstant(),
            firstSeen = rs.getTimestamp("first_seen")?.toInstant(),
            lastSeen = rs.getTimestamp("last_seen")?.toInstant(),
            active = rs.getBoolean("active"),
            lastAnalyzedAt = rs.getTimestamp("last_analyzed_at")?.toInstant(),
            raw = rs.getString("raw"),
        )
    }
}
