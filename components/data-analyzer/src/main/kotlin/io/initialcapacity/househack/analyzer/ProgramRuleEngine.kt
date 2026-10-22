package io.initialcapacity.househack.analyzer

import io.initialcapacity.househack.profile.BuyerProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Evaluates every program in [rules] against a buyer profile and a listing.
 * Programs sharing an `exclusive_group` cannot be combined; only the best one counts toward [ProgramOutcome.totalAssistance].
 */
class ProgramRuleEngine(private val rules: List<ProgramRule>) {

    fun evaluate(profile: BuyerProfile, price: Double, units: Int, loanAmount: Double): ProgramOutcome {
        val results = rules.map { rule ->
            when (rule.program) {
                "FHA_LIMITS" -> fhaLimits(rule, units, loanAmount)
                "HTH" -> hometownHeroes(rule, profile, loanAmount)
                "BREVARD_HFA" -> brevardHfa(rule, profile)
                "SHIP" -> ship(rule, profile)
                "HOMESTEAD" -> ProgramResult(rule.program, true, 0.0, listOf("Owner-occupied unit qualifies for the homestead exemption."), rule.group())
                else -> ProgramResult(rule.program, false, 0.0, listOf("Unknown program."), rule.group())
            }
        }

        val eligible = results.filter { it.eligible && it.assistance > 0 }
        val ungrouped = eligible.filter { it.exclusiveGroup == null }.sumOf { it.assistance }
        val grouped = eligible.filter { it.exclusiveGroup != null }.groupBy { it.exclusiveGroup }.values.sumOf { group -> group.maxOf { it.assistance } }

        return ProgramOutcome(results, ungrouped + grouped)
    }

    private fun fhaLimits(rule: ProgramRule, units: Int, loanAmount: Double): ProgramResult {
        val limit = rule.params.double(units.toString())
        return when {
            limit == null -> ProgramResult(rule.program, false, 0.0, listOf("No FHA limit for $units units."), rule.group())
            loanAmount > limit -> ProgramResult(rule.program, false, 0.0, listOf("Loan ${money(loanAmount)} exceeds the FHA $units-unit limit of ${money(limit)}."), rule.group())
            else -> ProgramResult(rule.program, true, 0.0, listOf("Loan ${money(loanAmount)} is within the FHA $units-unit limit of ${money(limit)}."), rule.group())
        }
    }

    private fun hometownHeroes(rule: ProgramRule, p: BuyerProfile, loanAmount: Double): ProgramResult {
        val reasons = mutableListOf<String>()
        val params = rule.params

        if (params.bool("requires_first_time_unless_veteran") == true && !p.firstTimeBuyer && !p.veteran) reasons += "Requires a first-time buyer (veterans exempt)."
        if (!p.hthEligibleOccupation && !p.veteran) reasons += "Requires an eligible occupation (healthcare, school staff, first responder, public safety, child care) or veteran status."
        params.double("min_credit")?.let { if (p.creditScore < it) reasons += "Credit score ${p.creditScore} is below the ${it.toInt()} minimum." }

        val tbaLimit = params.double("income_limit_tba")
        val bondLimit = if (p.householdSize <= 2) params.double("income_limit_bond_1_2") else params.double("income_limit_bond_3plus")
        val passesTba = tbaLimit != null && p.annualIncome <= tbaLimit
        val passesBond = bondLimit != null && p.annualIncome <= bondLimit
        if (!passesTba && !passesBond) reasons += "Income ${money(p.annualIncome)} exceeds both the TBA (${money(tbaLimit ?: 0.0)}) and Bond (${money(bondLimit ?: 0.0)}) limits."

        if (reasons.isNotEmpty()) return ProgramResult(rule.program, false, 0.0, reasons, rule.group())

        val assistance = (loanAmount * (params.double("pct_of_loan") ?: 0.05))
            .coerceIn(params.double("min") ?: 0.0, params.double("max") ?: Double.MAX_VALUE)
        val flavor = if (passesTba) "TBA (borrower income)" else "Bond (household income)"
        return ProgramResult(rule.program, true, assistance, listOf("Eligible under the $flavor test; 5% of loan, clamped to program limits."), rule.group())
    }

    private fun brevardHfa(rule: ProgramRule, p: BuyerProfile): ProgramResult {
        if (rule.params.bool("requires_first_time") == true && !p.firstTimeBuyer) {
            return ProgramResult(rule.program, false, 0.0, listOf("Requires a first-time buyer."), rule.group())
        }
        val amount = rule.params.double("amount") ?: 0.0
        return ProgramResult(rule.program, true, amount, listOf("0% deferred second mortgage of ${money(amount)}; repayable on sale, refinance, or move-out."), rule.group())
    }

    private fun ship(rule: ProgramRule, p: BuyerProfile): ProgramResult {
        val reasons = mutableListOf<String>()
        val params = rule.params
        val sizeKey = p.householdSize.coerceIn(1, 4).toString()
        val ami50 = params.jsonObject("ami_50_by_household")?.double(sizeKey)
        val ami80 = params.jsonObject("ami_80_by_household")?.double(sizeKey)

        if (!p.firstTimeBuyer) reasons += "Requires a first-time buyer (no ownership in the last 3 years)."
        params.double("max_liquid_assets")?.let { if (p.liquidSavings > it) reasons += "Liquid assets ${money(p.liquidSavings)} exceed the ${money(it)} cap." }
        if (ami80 == null || p.annualIncome > ami80) reasons += "Income ${money(p.annualIncome)} is above 80% of area median income (${money(ami80 ?: 0.0)}) for a household of ${p.householdSize}."

        if (reasons.isNotEmpty()) return ProgramResult(rule.program, false, 0.0, reasons, rule.group())

        val veryLow = ami50 != null && p.annualIncome <= ami50
        val amount = if (veryLow) params.double("max_very_low") ?: 0.0 else params.double("max_low") ?: 0.0
        val band = if (veryLow) "at or below 50% AMI" else "51-80% AMI"
        return ProgramResult(rule.program, true, amount, listOf("Income is $band; maximum assistance ${money(amount)} (actual award is the minimum needed)."), rule.group())
    }

    private fun ProgramRule.group(): String? = params["exclusive_group"]?.let { (it as? JsonPrimitive)?.contentOrNull }
    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.jsonObject(key: String): JsonObject? = this[key]?.let { if (it is JsonObject) it else null }
    private fun money(v: Double) = "$" + String.format("%,.0f", v)
}
