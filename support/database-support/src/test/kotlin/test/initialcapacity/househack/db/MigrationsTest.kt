package test.initialcapacity.househack.db

import io.initialcapacity.househack.db.DatabaseTemplate
import io.initialcapacity.househack.db.Migrations
import org.junit.Test
import kotlin.test.assertTrue

class MigrationsTest {
    @Test
    fun migrate_creates_all_tables() {
        Migrations.run(TestDatabase.dataSource)

        val tables = DatabaseTemplate(TestDatabase.dataSource).query(
            "select table_name from information_schema.tables where table_schema = 'public'"
        ) { it.getString(1) }

        assertTrue(
            tables.containsAll(
                listOf("profiles", "listings", "parcel_facts", "rent_estimates", "analyses", "alert_events", "program_rules", "api_budget")
            ),
            "missing tables, found: $tables"
        )
    }
}
