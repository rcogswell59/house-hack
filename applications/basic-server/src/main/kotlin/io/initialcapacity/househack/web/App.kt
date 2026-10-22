package io.initialcapacity.househack.web

import freemarker.cache.ClassTemplateLoader
import io.initialcapacity.househack.db.DatabaseConfiguration
import io.initialcapacity.househack.db.Migrations
import io.initialcapacity.househack.rentcast.FakeRentCastClient
import io.initialcapacity.househack.rentcast.HttpRentCastClient
import io.initialcapacity.househack.rentcast.RentCastClient
import io.initialcapacity.househack.wiring.Dependencies
import io.initialcapacity.househack.wiring.Workers
import io.initialcapacity.househack.wiring.healthRoute
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.freemarker.FreeMarker
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.TimeZone

private val logger = LoggerFactory.getLogger(object {}.javaClass.enclosingClass)

fun Application.module(
    deps: Dependencies,
    startWorkers: Set<String> = emptySet(),
    rentCast: RentCastClient? = null,
    clock: Clock = Clock.systemUTC(),
) {
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }
    install(ContentNegotiation) {
        json(Json { prettyPrint = true; ignoreUnknownKeys = true })
    }
    install(StatusPages) {
        exception<NotFoundException> { call, e -> call.respondText(e.message ?: "not found", status = HttpStatusCode.NotFound) }
    }
    install(Routing) {
        get("/") { call.respondRedirect("/report") }
        profileRoutes(deps)
        reportRoutes(deps)
        apiRoutes(deps)
        healthRoute(deps)
        staticResources("/static/styles", "static/styles")
        staticResources("/static/images", "static/images")
    }

    if (startWorkers.isNotEmpty()) {
        Workers.start(deps, startWorkers, rentCast ?: FakeRentCastClient(), clock)
    }
}

class NotFoundException(message: String) : RuntimeException(message)

fun main() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val port = System.getenv("PORT")?.toInt() ?: 8888

    val configuration = DatabaseConfiguration.fromEnv()
    Migrations.run(configuration.dataSource)
    val deps = Dependencies(configuration.dataSource)

    val rentCast = System.getenv("RENTCAST_API_KEY")?.takeIf { it.isNotBlank() }?.let { HttpRentCastClient(it) }
        ?: FakeRentCastClient().also { logger.warn("RENTCAST_API_KEY not set; using canned sample listings") }
    val workers = System.getenv("RUN_WORKERS")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    logger.info("starting web app on port {} with workers {}", port, workers)
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = { module(deps, workers, rentCast) }).start(wait = true)
}
