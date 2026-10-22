package test.initialcapacity.househack.analyzer

import io.initialcapacity.househack.analyzer.FinancingCalculator
import io.initialcapacity.househack.analyzer.FinancingInputs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinancingCalculatorTest {
    /** Golden duplex from the design spec: $400k, 2 units, $1,800/unit rent, 1.6% millage, 6.5% rate. */
    private val golden = FinancingInputs(
        price = 400_000.0, units = 2, rentPerUnit = 1_800.0, millage = 0.016, mortgageRate = 0.065,
        insuranceRate = 0.012, vacancyRate = 0.08, maintenanceRate = 0.05, closingCostRate = 0.03,
        monthlyIncome = 118_000.0 / 12, otherMonthlyDebts = 0.0, assistanceTotal = 15_000.0,
    )

    @Test
    fun amortized_payment_matches_standard_formula() {
        assertEquals(2482.5, FinancingCalculator.amortizedPayment(392_755.0, 0.065), 1.0)
    }

    @Test
    fun annual_taxes_apply_homestead_to_owner_share_only() {
        // owner share 200k - 50k exemption = 150k, plus rented share 200k => 350k * 0.016
        assertEquals(5_600.0, FinancingCalculator.annualTaxes(400_000.0, 2, 0.016, 50_000.0), 0.01)
    }

    @Test
    fun annual_taxes_never_go_below_zero_on_owner_share() {
        // cheap fourplex: owner share 25k < 50k exemption => owner share taxed at 0, rest in full
        assertEquals(75_000.0 * 0.016, FinancingCalculator.annualTaxes(100_000.0, 4, 0.016, 50_000.0), 0.01)
    }

    @Test
    fun buyer_view_golden_duplex() {
        val v = FinancingCalculator.buyerView(golden)

        assertEquals(392_755.0, v.loanAmount, 0.01)        // 386,000 base + 1.75% UFMIP financed
        assertEquals(2482.5, v.principalAndInterest, 1.0)
        assertEquals(180.01, v.monthlyMip, 0.05)
        assertEquals(466.67, v.monthlyTaxes, 0.05)
        assertEquals(400.0, v.monthlyInsurance, 0.01)
        assertEquals(234.0, v.reserves, 0.01)               // 1800 * (0.08 + 0.05)
        assertEquals(1800.0, v.otherUnitsRent, 0.01)
        assertEquals(1963.2, v.netMonthlyCost, 1.5)
        assertEquals(11_000.0, v.cashToClose, 0.01)         // 14,000 down + 12,000 closing - 15,000 assistance
    }

    @Test
    fun lender_view_golden_duplex_passes_dti_and_has_no_self_sufficiency() {
        val l = FinancingCalculator.lenderView(golden)

        assertEquals(1350.0, l.qualifyingRent, 0.01)
        assertEquals(3529.2, l.piti, 1.5)
        assertEquals(0.222, l.dti, 0.002)
        assertTrue(l.dtiPass)
        assertNull(l.selfSufficiencyRatio)
        assertNull(l.selfSufficiencyPass)
        assertTrue(l.pass)
    }

    @Test
    fun lender_view_triplex_self_sufficiency_ratio() {
        val l = FinancingCalculator.lenderView(golden.copy(price = 500_000.0, units = 3, rentPerUnit = 1_500.0))

        assertEquals(0.75 * 4_500.0 / l.piti, l.selfSufficiencyRatio!!, 0.001)
        assertEquals(l.selfSufficiencyRatio!! >= 1.0, l.selfSufficiencyPass)
        assertEquals(l.dtiPass && l.selfSufficiencyPass!!, l.pass)
    }

    @Test
    fun lender_view_fails_when_dti_exceeds_max() {
        val l = FinancingCalculator.lenderView(golden.copy(monthlyIncome = 4_000.0))

        assertTrue(l.dti > 0.43)
        assertTrue(!l.dtiPass)
        assertTrue(!l.pass)
    }
}
