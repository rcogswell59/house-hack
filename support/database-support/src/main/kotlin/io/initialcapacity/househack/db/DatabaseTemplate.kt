package io.initialcapacity.househack.db

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class DatabaseTemplate(private val dataSource: DataSource) {

    fun <T> query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): List<T> =
        dataSource.connection.use { connection -> query(connection, sql, params, mapper) }

    fun <T> queryOne(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): T? =
        query(sql, *params, mapper = mapper).firstOrNull()

    fun update(sql: String, vararg params: Any?): Int =
        dataSource.connection.use { connection -> update(connection, sql, params) }

    fun <T> inTransaction(block: (Connection) -> T): T =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                result
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }

    companion object {
        fun <T> query(connection: Connection, sql: String, params: Array<out Any?>, mapper: (ResultSet) -> T): List<T> =
            connection.prepareStatement(sql).use { statement ->
                bind(statement, params)
                statement.executeQuery().use { rs ->
                    val results = mutableListOf<T>()
                    while (rs.next()) results.add(mapper(rs))
                    results
                }
            }

        fun update(connection: Connection, sql: String, params: Array<out Any?>): Int =
            connection.prepareStatement(sql).use { statement ->
                bind(statement, params)
                statement.executeUpdate()
            }

        private fun bind(statement: PreparedStatement, params: Array<out Any?>) {
            params.forEachIndexed { index, param ->
                when (param) {
                    is Instant -> statement.setTimestamp(index + 1, Timestamp.from(param))
                    else -> statement.setObject(index + 1, param)
                }
            }
        }
    }
}
