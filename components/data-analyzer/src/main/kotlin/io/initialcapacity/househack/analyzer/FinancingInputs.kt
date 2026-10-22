package io.initialcapacity.househack.analyzer

import kotlinx.serialization.Serializable

/** Everything the financing math needs for one listing and one buyer. Rates are fractions (0.065 = 6.5%). */
data class FinancingInputs(
    val price: Double,
    val units: Int,
    val rentPerUnit: Double,
    val millage: Double,
    val mortgageRate: Double,
    val insuranceRate: Double,
    val vacancyRate: Double,
    val maintenanceRate: Double,
    val closingCostRate: Double,
    val monthlyIncome: Double,
    val otherMonthlyDebts: Double,
    val assistanceTotal: Double,
    val downPaymentRate: Double = 0.035,
    val upfrontMipRate: Double = 0.0175,
    val annualMipRate: Double = 0.0055,
    val homesteadExemption: Double = 50_000.0,
    val maxDti: Double = 0.43,
)

/** What the buyer actually pays each month and brings to closing. The ranking number is [netMonthlyCost]. */
@Serializable
data class BuyerView(
    val loanAmount: Double,
    val principalAndInterest: Double,
    val monthlyMip: Double,
    val monthlyTaxes: Double,
    val monthlyInsurance: Double,
    val reserves: Double,
    val otherUnitsRent: Double,
    val netMonthlyCost: Double,
    val cashToClose: Double,
)

/** FHA underwriting view: 75% of other-unit rent counts, and 3-4 units must be self-sufficient. Never blended with [BuyerView]. */
@Serializable
data class LenderView(
    val piti: Double,
    val qualifyingRent: Double,
    val dti: Double,
    val dtiPass: Boolean,
    val selfSufficiencyRatio: Double?,
    val selfSufficiencyPass: Boolean?,
    val pass: Boolean,
)
