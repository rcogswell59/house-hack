package io.initialcapacity.househack.profile

import java.time.Instant

data class BuyerProfile(
    val id: Long? = null,
    val name: String,
    val annualIncome: Double,
    val liquidSavings: Double,
    val creditScore: Int,
    val firstTimeBuyer: Boolean,
    val hthEligibleOccupation: Boolean,
    val veteran: Boolean,
    val householdSize: Int,
    val otherMonthlyDebts: Double,
    val workplaceLat: Double,
    val workplaceLng: Double,
    val maxDriveMinutes: Int,
    val unitsMin: Int,
    val unitsMax: Int,
    val vacancyRate: Double,
    val maintenanceRate: Double,
    val insuranceRate: Double,
    val closingCostRate: Double,
    val mortgageRate: Double,
    val loanProgram: String = "FHA",
    val roadFactor: Double,
    val avgSpeedMph: Double,
    val updatedAt: Instant? = null,
) {
    val monthlyIncome: Double get() = annualIncome / 12.0

    companion object {
        /** Seed profile: first-time buyer working on-site near Cape Canaveral. */
        fun defaults() = BuyerProfile(
            name = "Ryan",
            annualIncome = 118_000.0,
            liquidSavings = 20_000.0,
            creditScore = 720,
            firstTimeBuyer = true,
            hthEligibleOccupation = false,
            veteran = false,
            householdSize = 1,
            otherMonthlyDebts = 0.0,
            workplaceLat = 28.3922,
            workplaceLng = -80.6077,
            maxDriveMinutes = 30,
            unitsMin = 2,
            unitsMax = 4,
            vacancyRate = 0.08,
            maintenanceRate = 0.05,
            insuranceRate = 0.012,
            closingCostRate = 0.03,
            mortgageRate = 0.065,
            roadFactor = 1.4,
            avgSpeedMph = 30.0,
        )
    }
}
