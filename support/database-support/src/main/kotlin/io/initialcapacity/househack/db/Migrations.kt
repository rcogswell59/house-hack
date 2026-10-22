package io.initialcapacity.househack.db

import org.flywaydb.core.Flyway
import javax.sql.DataSource

object Migrations {
    fun run(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
