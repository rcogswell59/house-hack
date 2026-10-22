package io.initialcapacity.househack.listings

import io.initialcapacity.househack.db.DatabaseTemplate
import java.sql.ResultSet
import java.time.Instant

class ParcelFactRepository(private val db: DatabaseTemplate) {

    fun upsertAll(facts: List<ParcelFact>): Int = db.inTransaction { connection ->
        facts.sumOf { f ->
            DatabaseTemplate.update(
                connection,
                """
                insert into parcel_facts (parcel_id, address_norm, city, zip, just_value, assessed_value, taxes_annual, millage, year_built, units, land_use_code, imported_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, coalesce(?, now()))
                on conflict (parcel_id) do update set
                  address_norm = excluded.address_norm, city = excluded.city, zip = excluded.zip, just_value = excluded.just_value,
                  assessed_value = excluded.assessed_value, taxes_annual = excluded.taxes_annual, millage = excluded.millage,
                  year_built = excluded.year_built, units = excluded.units, land_use_code = excluded.land_use_code, imported_at = excluded.imported_at
                """,
                arrayOf(f.parcelId, f.addressNorm, f.city, f.zip, f.justValue, f.assessedValue, f.taxesAnnual, f.millage, f.yearBuilt, f.units, f.landUseCode, f.importedAt),
            )
        }
    }

    fun findByAddressNorm(addressNorm: String): ParcelFact? =
        db.queryOne("select * from parcel_facts where address_norm = ? order by parcel_id limit 1", addressNorm, mapper = ::map)

    fun linkListing(listingId: Long, parcelId: String) {
        db.update(
            "insert into listing_parcel (listing_id, parcel_id) values (?, ?) on conflict (listing_id) do update set parcel_id = excluded.parcel_id",
            listingId, parcelId,
        )
    }

    fun findForListing(listingId: Long): ParcelFact? = db.queryOne(
        "select p.* from parcel_facts p join listing_parcel lp on lp.parcel_id = p.parcel_id where lp.listing_id = ?",
        listingId, mapper = ::map,
    )

    private fun map(rs: ResultSet) = ParcelFact(
        parcelId = rs.getString("parcel_id"),
        addressNorm = rs.getString("address_norm"),
        city = rs.getString("city"),
        zip = rs.getString("zip"),
        justValue = rs.getBigDecimal("just_value")?.toDouble(),
        assessedValue = rs.getBigDecimal("assessed_value")?.toDouble(),
        taxesAnnual = rs.getBigDecimal("taxes_annual")?.toDouble(),
        millage = rs.getBigDecimal("millage")?.toDouble(),
        yearBuilt = rs.getObject("year_built") as Int?,
        units = rs.getObject("units") as Int?,
        landUseCode = rs.getString("land_use_code"),
        importedAt = rs.getTimestamp("imported_at")?.toInstant() as Instant?,
    )
}
