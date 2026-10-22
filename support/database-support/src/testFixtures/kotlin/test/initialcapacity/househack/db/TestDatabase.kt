package test.initialcapacity.househack.db

import io.initialcapacity.househack.db.DatabaseConfiguration
import io.initialcapacity.househack.db.DatabaseTemplate
import io.initialcapacity.househack.db.Migrations
import javax.sql.DataSource

/**
 * One migrated test database per JVM. Reads TEST_DATABASE_URL; falls back to the local dev role.
 */
object TestDatabase {
    private const val DEFAULT_URL = "jdbc:postgresql://localhost:5432/househack_test?user=househack&password=househack"

    val dataSource: DataSource by lazy {
        val url = System.getenv("TEST_DATABASE_URL") ?: DEFAULT_URL
        DatabaseConfiguration(url).dataSource.also { Migrations.run(it) }
    }

    private val template by lazy { DatabaseTemplate(dataSource) }

    fun truncateAll() {
        val tables = template.query(
            "select table_name from information_schema.tables where table_schema = 'public' and table_name not in ('flyway_schema_history', 'program_rules')"
        ) { it.getString(1) }
        if (tables.isNotEmpty()) {
            template.update("truncate ${tables.joinToString(", ")} restart identity cascade")
        }
    }
}
