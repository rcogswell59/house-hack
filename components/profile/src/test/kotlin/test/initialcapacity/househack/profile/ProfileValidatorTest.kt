package test.initialcapacity.househack.profile

import io.initialcapacity.househack.profile.BuyerProfile
import io.initialcapacity.househack.profile.ProfileValidator
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileValidatorTest {
    private val valid = BuyerProfile.defaults()

    @Test
    fun defaults_are_valid() {
        assertEquals(emptyList(), ProfileValidator.validate(valid))
    }

    @Test
    fun rejects_negative_income() {
        val errors = ProfileValidator.validate(valid.copy(annualIncome = -1.0))
        assertTrue(errors.any { it.contains("income", ignoreCase = true) }, "$errors")
    }

    @Test
    fun rejects_credit_out_of_range() {
        assertTrue(ProfileValidator.validate(valid.copy(creditScore = 299)).any { it.contains("credit", ignoreCase = true) })
        assertTrue(ProfileValidator.validate(valid.copy(creditScore = 851)).any { it.contains("credit", ignoreCase = true) })
    }

    @Test
    fun rejects_units_min_above_max() {
        val errors = ProfileValidator.validate(valid.copy(unitsMin = 4, unitsMax = 2))
        assertTrue(errors.any { it.contains("units", ignoreCase = true) }, "$errors")
    }
}
