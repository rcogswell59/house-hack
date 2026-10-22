package test.initialcapacity.househack.db

import io.initialcapacity.househack.db.jdbcUrl
import org.junit.Test
import kotlin.test.assertEquals

class DatabaseConfigurationTest {
    @Test
    fun jdbcUrl_converts_postgres_scheme() {
        assertEquals(
            "jdbc:postgresql://h.render.com:5432/db?user=u&password=p",
            jdbcUrl("postgres://u:p@h.render.com:5432/db")
        )
    }

    @Test
    fun jdbcUrl_passes_jdbc_through() {
        assertEquals("jdbc:postgresql://localhost/x", jdbcUrl("jdbc:postgresql://localhost/x"))
    }
}
