package io.initialcapacity.househack.wiring

import io.initialcapacity.househack.analyzer.ProgramRuleRepository
import io.initialcapacity.househack.db.DatabaseTemplate
import io.initialcapacity.househack.listings.AnalysisRepository
import io.initialcapacity.househack.listings.ApiBudgetRepository
import io.initialcapacity.househack.listings.ListingRepository
import io.initialcapacity.househack.listings.ParcelFactRepository
import io.initialcapacity.househack.listings.RentEstimateRepository
import io.initialcapacity.househack.profile.ProfileRepository
import javax.sql.DataSource

/** Every repository the web app and the in-process workers share, built once per process. */
class Dependencies(val dataSource: DataSource) {
    val db = DatabaseTemplate(dataSource)
    val profiles = ProfileRepository(db)
    val listings = ListingRepository(db)
    val parcels = ParcelFactRepository(db)
    val rents = RentEstimateRepository(db)
    val analyses = AnalysisRepository(db)
    val budget = ApiBudgetRepository(db)
    val programRules = ProgramRuleRepository(db)
}
