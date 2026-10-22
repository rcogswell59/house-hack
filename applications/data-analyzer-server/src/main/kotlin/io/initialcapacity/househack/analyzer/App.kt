package io.initialcapacity.househack.analyzer

import io.initialcapacity.househack.db.DatabaseConfiguration
import io.initialcapacity.househack.db.Migrations
import io.initialcapacity.househack.rentcast.FakeRentCastClient
import io.initialcapacity.househack.wiring.Dependencies
import io.initialcapacity.househack.wiring.Workers
import io.initialcapacity.househack.wiring.healthRoute
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.TimeZone

private val logger = LoggerFactory.getLogger(object {}.javaClass.enclosingClass)

/** Data analyzer: polls for stale listings every minute and writes analyses. Serves only /health. */
fun Application.module(deps: Dependencies, startWorkers: Boolean = true) {
    install(ContentNegotiation) { json(Json { prettyPrint = true }) }
    install(Routing) { healthRoute(deps) }

    if (startWorkers) Workers.start(deps, setOf(Workers.ANALYZER), FakeRentCastClient())
}

fun main() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val port = System.getenv("PORT")?.toInt() ?: 8887

    val configuration = DatabaseConfiguration.fromEnv()
    Migrations.run(configuration.dataSource)
    val deps = Dependencies(configuration.dataSource)

    logger.info("starting data analyzer on port {}", port)
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = { module(deps) }).start(wait = true)
}
