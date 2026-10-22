package io.initialcapacity.househack.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.net.URI
import javax.sql.DataSource

/**
 * Converts a Render/Heroku style `postgres://user:pass@host:port/db` URL into a JDBC URL.
 * A URL that already starts with `jdbc:` is returned unchanged.
 */
fun jdbcUrl(raw: String): String {
    if (raw.startsWith("jdbc:")) return raw

    val uri = URI(raw)
    val (user, password) = uri.userInfo.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val port = if (uri.port == -1) "" else ":${uri.port}"

    return "jdbc:postgresql://${uri.host}$port${uri.path}?user=$user&password=$password"
}

class DatabaseConfiguration(val jdbcUrl: String) {
    val dataSource: DataSource by lazy {
        HikariDataSource(HikariConfig().apply {
            this.jdbcUrl = this@DatabaseConfiguration.jdbcUrl
            maximumPoolSize = 5
        })
    }

    companion object {
        fun fromEnv(name: String = "DATABASE_URL"): DatabaseConfiguration {
            val raw = System.getenv(name) ?: throw IllegalStateException("$name is not set")
            return DatabaseConfiguration(jdbcUrl(raw))
        }
    }
}
