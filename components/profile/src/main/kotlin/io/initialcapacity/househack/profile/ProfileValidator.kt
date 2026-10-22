package io.initialcapacity.househack.profile

object ProfileValidator {
    fun validate(p: BuyerProfile): List<String> {
        val errors = mutableListOf<String>()

        if (p.name.isBlank()) errors += "Name is required."
        if (p.annualIncome <= 0) errors += "Annual income must be greater than zero."
        if (p.liquidSavings < 0) errors += "Liquid savings cannot be negative."
        if (p.creditScore !in 300..850) errors += "Credit score must be between 300 and 850."
        if (p.householdSize < 1) errors += "Household size must be at least 1."
        if (p.otherMonthlyDebts < 0) errors += "Other monthly debts cannot be negative."
        if (p.maxDriveMinutes !in 1..180) errors += "Max drive minutes must be between 1 and 180."
        if (p.unitsMin < 2 || p.unitsMax > 4 || p.unitsMin > p.unitsMax) errors += "Units must be a range within 2 to 4."
        if (p.mortgageRate !in 0.0..0.25) errors += "Mortgage rate must be between 0 and 0.25."
        if (p.roadFactor < 1.0) errors += "Road factor must be at least 1.0."
        if (p.avgSpeedMph <= 0) errors += "Average speed must be greater than zero."

        listOf(
            "Vacancy rate" to p.vacancyRate,
            "Maintenance rate" to p.maintenanceRate,
            "Insurance rate" to p.insuranceRate,
            "Closing cost rate" to p.closingCostRate,
        ).forEach { (label, rate) -> if (rate !in 0.0..1.0) errors += "$label must be between 0 and 1." }

        return errors
    }
}
