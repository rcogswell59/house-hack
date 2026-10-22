package io.initialcapacity.househack.collector

import io.initialcapacity.househack.db.DatabaseConfiguration
import io.initialcapacity.househack.db.Migrations
import io.initialcapacity.househack.rentcast.FakeRentCastClient
import io.initialcapacity.househack.rentcast.HttpRentCastClient
import io.initialcapacity.househack.rentcast.RentCastClient
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

/** Data collector: daily RentCast listings, parcel import, and rent estimates. Serves only /health. */
fun Application.module(deps: Dependencies, startWorkers: Boolean = true, rentCast: RentCastClient = FakeRentCastClient()) {
    install(ContentNegotiation) { json(Json { prettyPrint = true }) }
    install(Routing) { healthRoute(deps) }

    if (startWorkers) Workers.start(deps, setOf(Workers.COLLECTOR), rentCast)
}

fun main() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val port = System.getenv("PORT")?.toInt() ?: 8886

    val configuration = DatabaseConfiguration.fromEnv()
    Migrations.run(configuration.dataSource)
    val deps = Dependencies(configuration.dataSource)
    val rentCast = System.getenv("RENTCAST_API_KEY")?.takeIf { it.isNotBlank() }?.let { HttpRentCastClient(it) }
        ?: FakeRentCastClient().also { logger.warn("RENTCAST_API_KEY not set; using canned sample listings") }

    logger.info("starting data collector on port {}", port)
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = { module(deps, rentCast = rentCast) }).start(wait = true)
}
