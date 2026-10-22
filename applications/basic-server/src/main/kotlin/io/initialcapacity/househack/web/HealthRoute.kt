package io.initialcapacity.househack.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(val status: String, val db: String, val activeListings: Int = 0, val pendingListings: Int = 0, val error: String? = null)

fun Route.healthRoute(deps: Dependencies) {
    get("/health") {
        val health = runCatching {
            deps.db.queryOne("select 1") { it.getInt(1) }
            val active = deps.listings.findActive().size
            val pending = deps.profiles.findFirst()?.id?.let { deps.analyses.countPendingForProfile(it) } ?: active
            HealthDto("ok", "ok", active, pending)
        }.getOrElse { HealthDto("degraded", "down", error = it.message) }

        call.respond(if (health.db == "ok") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable, health)
    }
}
