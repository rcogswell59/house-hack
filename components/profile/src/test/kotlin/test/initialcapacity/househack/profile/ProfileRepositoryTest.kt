package test.initialcapacity.househack.profile

import io.initialcapacity.househack.db.DatabaseTemplate
import io.initialcapacity.househack.profile.BuyerProfile
import io.initialcapacity.househack.profile.ProfileRepository
import org.junit.Before
import org.junit.Test
import test.initialcapacity.househack.db.TestDatabase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileRepositoryTest {
    private val repository = ProfileRepository(DatabaseTemplate(TestDatabase.dataSource))

    @Before
    fun setUp() = TestDatabase.truncateAll()

    @Test
    fun save_then_findFirst_roundtrips_every_field() {
        val saved = repository.save(BuyerProfile.defaults().copy(name = "Test", veteran = true, otherMonthlyDebts = 250.0, roadFactor = 1.3))

        val found = repository.findFirst()

        assertNotNull(found)
        assertNotNull(found.id)
        assertNotNull(found.updatedAt)
        assertEquals(saved.copy(id = null, updatedAt = null), found.copy(id = null, updatedAt = null))
        assertEquals("Test", found.name)
        assertEquals(true, found.veteran)
        assertEquals(250.0, found.otherMonthlyDebts)
        assertEquals(1.3, found.roadFactor)
        assertEquals(118_000.0, found.annualIncome)
        assertEquals(28.3922, found.workplaceLat)
    }

    @Test
    fun save_with_id_updates_and_bumps_updated_at() {
        val first = repository.save(BuyerProfile.defaults())
        Thread.sleep(20)

        val updated = repository.save(first.copy(annualIncome = 125_000.0))

        assertEquals(first.id, updated.id)
        assertEquals(125_000.0, repository.findFirst()!!.annualIncome)
        assertTrue(updated.updatedAt!!.isAfter(first.updatedAt!!), "updated_at should move forward")
        assertEquals(1, DatabaseTemplate(TestDatabase.dataSource).queryOne("select count(*) from profiles") { it.getInt(1) })
    }
}
