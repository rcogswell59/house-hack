package io.initialcapacity.househack.profile

import io.initialcapacity.househack.db.DatabaseTemplate
import java.sql.ResultSet

class ProfileRepository(private val db: DatabaseTemplate) {

    fun save(p: BuyerProfile): BuyerProfile {
        val values = arrayOf(
            p.name, p.annualIncome, p.liquidSavings, p.creditScore, p.firstTimeBuyer, p.hthEligibleOccupation, p.veteran,
            p.householdSize, p.otherMonthlyDebts, p.workplaceLat, p.workplaceLng, p.maxDriveMinutes, p.unitsMin, p.unitsMax,
            p.vacancyRate, p.maintenanceRate, p.insuranceRate, p.closingCostRate, p.mortgageRate, p.loanProgram,
            p.roadFactor, p.avgSpeedMph,
        )
        val sql = if (p.id == null) {
            "insert into profiles ($COLUMNS) values (${"?,".repeat(values.size).trimEnd(',')}) returning *"
        } else {
            "update profiles set ${COLUMNS.split(", ").joinToString(", ") { "$it = ?" }}, updated_at = now() where id = ? returning *"
        }
        val params = if (p.id == null) values else arrayOf(*values, p.id)

        return db.queryOne(sql, *params, mapper = ::map) ?: throw IllegalStateException("profile ${p.id} not found")
    }

    fun findFirst(): BuyerProfile? = db.queryOne("select * from profiles order by id limit 1", mapper = ::map)

    private fun map(rs: ResultSet) = BuyerProfile(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        annualIncome = rs.getDouble("annual_income"),
        liquidSavings = rs.getDouble("liquid_savings"),
        creditScore = rs.getInt("credit_score"),
        firstTimeBuyer = rs.getBoolean("first_time_buyer"),
        hthEligibleOccupation = rs.getBoolean("hth_eligible_occupation"),
        veteran = rs.getBoolean("veteran"),
        householdSize = rs.getInt("household_size"),
        otherMonthlyDebts = rs.getDouble("other_monthly_debts"),
        workplaceLat = rs.getDouble("workplace_lat"),
        workplaceLng = rs.getDouble("workplace_lng"),
        maxDriveMinutes = rs.getInt("max_drive_minutes"),
        unitsMin = rs.getInt("units_min"),
        unitsMax = rs.getInt("units_max"),
        vacancyRate = rs.getDouble("vacancy_rate"),
        maintenanceRate = rs.getDouble("maintenance_rate"),
        insuranceRate = rs.getDouble("insurance_rate"),
        closingCostRate = rs.getDouble("closing_cost_rate"),
        mortgageRate = rs.getDouble("mortgage_rate"),
        loanProgram = rs.getString("loan_program"),
        roadFactor = rs.getDouble("road_factor"),
        avgSpeedMph = rs.getDouble("avg_speed_mph"),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    companion object {
        private const val COLUMNS = "name, annual_income, liquid_savings, credit_score, first_time_buyer, hth_eligible_occupation, veteran, " +
            "household_size, other_monthly_debts, workplace_lat, workplace_lng, max_drive_minutes, units_min, units_max, " +
            "vacancy_rate, maintenance_rate, insurance_rate, closing_cost_rate, mortgage_rate, loan_program, road_factor, avg_speed_mph"
    }
}
