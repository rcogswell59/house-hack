package test.initialcapacity.househack.analyzer

import io.initialcapacity.househack.analyzer.ProgramRule
import io.initialcapacity.househack.analyzer.ProgramRuleEngine
import io.initialcapacity.househack.analyzer.ProgramRuleRepository
import io.initialcapacity.househack.db.DatabaseTemplate
import io.initialcapacity.househack.profile.BuyerProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgramRuleEngineTest {
    private val rules = listOf(
        rule("FHA_LIMITS", """{"1": 541287, "2": 693050, "3": 837700, "4": 1041125}"""),
        rule("HTH", """{"exclusive_group": "first_mortgage_dpa", "pct_of_loan": 0.05, "min": 10000, "max": 35000, "min_credit": 640,
            "income_limit_tba": 149400, "income_limit_bond_1_2": 101414, "income_limit_bond_3plus": 116627, "requires_first_time_unless_veteran": true}"""),
        rule("BREVARD_HFA", """{"exclusive_group": "first_mortgage_dpa", "amount": 15000, "requires_first_time": true}"""),
        rule("SHIP", """{"exclusive_group": null, "max_very_low": 75000, "max_low": 60000,
            "ami_80_by_household": {"1": 56500, "2": 64600, "3": 72650, "4": 80700},
            "ami_50_by_household": {"1": 35300, "2": 40350, "3": 45400, "4": 50450}, "max_liquid_assets": 15000}"""),
        rule("HOMESTEAD", """{"exemption": 50000}"""),
    )
    private val engine = ProgramRuleEngine(rules)
    private val ryan = BuyerProfile.defaults()
    private val goldenLoan = 392_755.0

    private fun rule(program: String, json: String) = ProgramRule(program, 2026, "Brevard", Json.parseToJsonElement(json).jsonObject)
    private fun result(outcome: io.initialcapacity.househack.analyzer.ProgramOutcome, program: String) = outcome.results.single { it.program == program }

    @Test
    fun ryan_is_ineligible_for_hth_without_occupation_or_veteran() {
        val hth = result(engine.evaluate(ryan, 400_000.0, 2, goldenLoan), "HTH")

        assertFalse(hth.eligible)
        assertTrue(hth.reasons.any { it.contains("occupation", ignoreCase = true) }, "${hth.reasons}")
        assertEquals(0.0, hth.assistance)
    }

    @Test
    fun veteran_with_income_under_limit_gets_hth_5pct_of_loan() {
        val hth = result(engine.evaluate(ryan.copy(veteran = true), 400_000.0, 2, goldenLoan), "HTH")

        assertTrue(hth.eligible, "${hth.reasons}")
        assertEquals(19_637.75, hth.assistance, 0.01)
    }

    @Test
    fun hth_assistance_clamps_to_min_and_max() {
        val vet = ryan.copy(veteran = true)
        assertEquals(10_000.0, result(engine.evaluate(vet, 160_000.0, 2, 150_000.0), "HTH").assistance, 0.01)
        assertEquals(35_000.0, result(engine.evaluate(vet, 950_000.0, 4, 900_000.0), "HTH").assistance, 0.01)
    }

    @Test
    fun hth_fails_on_credit_and_on_income_over_tba_limit() {
        val lowCredit = result(engine.evaluate(ryan.copy(veteran = true, creditScore = 600), 400_000.0, 2, goldenLoan), "HTH")
        val highIncome = result(engine.evaluate(ryan.copy(veteran = true, annualIncome = 150_000.0), 400_000.0, 2, goldenLoan), "HTH")

        assertFalse(lowCredit.eligible)
        assertTrue(lowCredit.reasons.any { it.contains("credit", ignoreCase = true) })
        assertFalse(highIncome.eligible)
        assertTrue(highIncome.reasons.any { it.contains("income", ignoreCase = true) })
    }

    @Test
    fun ryan_gets_brevard_hfa_15000() {
        val hfa = result(engine.evaluate(ryan, 400_000.0, 2, goldenLoan), "BREVARD_HFA")

        assertTrue(hfa.eligible)
        assertEquals(15_000.0, hfa.assistance)
        assertEquals("first_mortgage_dpa", hfa.exclusiveGroup)
    }

    @Test
    fun ryan_fails_ship_on_income_but_low_income_household_qualifies() {
        assertFalse(result(engine.evaluate(ryan, 400_000.0, 2, goldenLoan), "SHIP").eligible)

        val lowIncome = result(engine.evaluate(ryan.copy(annualIncome = 50_000.0, liquidSavings = 9_000.0), 250_000.0, 2, 245_000.0), "SHIP")
        assertTrue(lowIncome.eligible, "${lowIncome.reasons}")
        assertEquals(60_000.0, lowIncome.assistance)   // 51-80% AMI band
    }

    @Test
    fun exclusive_group_takes_best_single_program() {
        val outcome = engine.evaluate(ryan.copy(veteran = true), 400_000.0, 2, goldenLoan)

        // HTH (19,637.75) and HFA (15,000) are both first-mortgage DPA: only the best counts.
        assertEquals(19_637.75, outcome.totalAssistance, 0.01)
    }

    @Test
    fun total_assistance_for_ryan_is_hfa_only() {
        assertEquals(15_000.0, engine.evaluate(ryan, 400_000.0, 2, goldenLoan).totalAssistance, 0.01)
    }

    @Test
    fun price_above_fha_limit_marks_fha_ineligible() {
        val fha = result(engine.evaluate(ryan, 750_000.0, 2, 740_000.0), "FHA_LIMITS")

        assertFalse(fha.eligible)
        assertTrue(fha.reasons.any { it.contains("693") }, "${fha.reasons}")
        assertTrue(result(engine.evaluate(ryan, 650_000.0, 2, 640_000.0), "FHA_LIMITS").eligible)
    }

    @Test
    fun homestead_is_always_eligible_for_owner_occupant() {
        assertTrue(result(engine.evaluate(ryan, 400_000.0, 2, goldenLoan), "HOMESTEAD").eligible)
    }

    @Test
    fun repository_reads_seeded_2026_rules() {
        val seeded = ProgramRuleRepository(DatabaseTemplate(TestDatabase.dataSource)).forYear(2026, "Brevard")

        assertEquals(setOf("FHA_LIMITS", "HTH", "BREVARD_HFA", "SHIP", "HOMESTEAD"), seeded.map { it.program }.toSet())
        assertEquals(693050.0, seeded.single { it.program == "FHA_LIMITS" }.params["2"].toString().toDouble())
    }
}
