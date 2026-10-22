package test.initialcapacity.househack.listings

import io.initialcapacity.househack.listings.ApiBudgetRepository
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiBudgetRepositoryTest {
    private val budget = ApiBudgetRepository(Fixtures.db)
    private val month = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() = TestDatabase.truncateAll()

    @Test
    fun tryReserve_allows_up_to_limit_then_refuses() {
        assertTrue(budget.tryReserve("rentcast", month, limit = 3))
        assertTrue(budget.tryReserve("rentcast", month, limit = 3))
        assertTrue(budget.tryReserve("rentcast", month, limit = 3))
        assertFalse(budget.tryReserve("rentcast", month, limit = 3))

        assertEquals(3, budget.callsUsed("rentcast", month))
        assertEquals(0, budget.callsUsed("rentcast", month.plusMonths(1)))
    }

    @Test
    fun tryReserve_refuses_batch_that_would_exceed_limit() {
        assertTrue(budget.tryReserve("rentcast", month, limit = 5, n = 4))
        assertFalse(budget.tryReserve("rentcast", month, limit = 5, n = 2))
        assertEquals(4, budget.callsUsed("rentcast", month))
    }
}
