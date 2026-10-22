package io.initialcapacity.househack.web

import io.initialcapacity.househack.analyzer.Breakdown
import io.initialcapacity.househack.analyzer.ListingAnalyzer
import io.initialcapacity.househack.listings.Analysis
import io.initialcapacity.househack.listings.Listing
import io.initialcapacity.househack.profile.BuyerProfile
import io.initialcapacity.househack.profile.ProfileValidator
import io.initialcapacity.househack.wiring.Dependencies
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/** JSON surface for other clients (and the course's REST requirement). Same repositories as the pages. */
fun Route.apiRoutes(deps: Dependencies) {
    get("/api/profile") {
        val profile = deps.profiles.findFirst() ?: throw NotFoundException("no profile yet")
        call.respond(ProfileDto.from(profile))
    }

    put("/api/profile") {
        val dto = call.receive<ProfileDto>()
        val profile = dto.toProfile(existingId = deps.profiles.findFirst()?.id)
        val errors = ProfileValidator.validate(profile)
        if (errors.isNotEmpty()) {
            call.respond(HttpStatusCode.BadRequest, ErrorsDto(errors))
            return@put
        }
        call.respond(ProfileDto.from(deps.profiles.save(profile)))
    }

    get("/api/listings") {
        val profile = deps.profiles.findFirst() ?: run { call.respond(emptyList<ListingRowDto>()); return@get }
        val rows = deps.analyses.rankedForProfile(profile.id!!).map { (a, l) -> ListingRowDto.of(a, l) }
        call.respond(rows)
    }

    get("/api/listings/{id}") {
        val id = call.parameters["id"]?.toLongOrNull() ?: throw NotFoundException("listing not found")
        val listing = deps.listings.findById(id) ?: throw NotFoundException("listing $id not found")
        val analysis = deps.profiles.findFirst()?.id?.let { deps.analyses.findForListing(id, it) }
        call.respond(buildJsonObject {
            put("listing", json.encodeToJsonElement(ListingDto.from(listing)))
            put("analysis", analysis?.let { AnalysisDto.from(it) } ?: kotlinx.serialization.json.JsonNull)
        })
    }
}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class ErrorsDto(val errors: List<String>)

@Serializable
data class ProfileDto(
    val id: Long? = null, val name: String, val annualIncome: Double, val liquidSavings: Double, val creditScore: Int,
    val firstTimeBuyer: Boolean, val hthEligibleOccupation: Boolean = false, val veteran: Boolean = false, val householdSize: Int = 1,
    val otherMonthlyDebts: Double = 0.0, val workplaceLat: Double, val workplaceLng: Double, val maxDriveMinutes: Int,
    val unitsMin: Int = 2, val unitsMax: Int = 4, val vacancyRate: Double = 0.08, val maintenanceRate: Double = 0.05,
    val insuranceRate: Double = 0.012, val closingCostRate: Double = 0.03, val mortgageRate: Double = 0.065,
    val loanProgram: String = "FHA", val roadFactor: Double = 1.4, val avgSpeedMph: Double = 30.0, val updatedAt: String? = null,
) {
    fun toProfile(existingId: Long?) = BuyerProfile(
        existingId, name, annualIncome, liquidSavings, creditScore, firstTimeBuyer, hthEligibleOccupation, veteran, householdSize,
        otherMonthlyDebts, workplaceLat, workplaceLng, maxDriveMinutes, unitsMin, unitsMax, vacancyRate, maintenanceRate,
        insuranceRate, closingCostRate, mortgageRate, loanProgram, roadFactor, avgSpeedMph,
    )

    companion object {
        fun from(p: BuyerProfile) = ProfileDto(
            p.id, p.name, p.annualIncome, p.liquidSavings, p.creditScore, p.firstTimeBuyer, p.hthEligibleOccupation, p.veteran,
            p.householdSize, p.otherMonthlyDebts, p.workplaceLat, p.workplaceLng, p.maxDriveMinutes, p.unitsMin, p.unitsMax,
            p.vacancyRate, p.maintenanceRate, p.insuranceRate, p.closingCostRate, p.mortgageRate, p.loanProgram, p.roadFactor,
            p.avgSpeedMph, p.updatedAt?.toString(),
        )
    }
}

@Serializable
data class ListingRowDto(
    val listingId: Long, val address: String, val city: String?, val price: Double, val units: Int,
    val netMonthlyCost: Double, val cashToClose: Double, val lenderPass: Boolean, val driveMinutes: Double,
) {
    companion object {
        fun of(a: Analysis, l: Listing): ListingRowDto {
            val units = ListingAnalyzer.json.decodeFromString<Breakdown>(a.breakdownJson).units
            return ListingRowDto(l.id!!, l.address, l.city, l.price, units, a.netMonthlyCost, a.cashToClose, a.lenderPass, a.driveMinutes)
        }
    }
}

@Serializable
data class ListingDto(
    val id: Long, val source: String, val sourceId: String, val address: String, val city: String?, val zip: String?,
    val lat: Double?, val lng: Double?, val price: Double, val bedrooms: Int?, val bathrooms: Double?, val sqft: Int?,
    val yearBuilt: Int?, val propertyType: String?, val active: Boolean, val firstSeen: String?, val lastSeen: String?,
) {
    companion object {
        fun from(l: Listing) = ListingDto(l.id!!, l.source, l.sourceId, l.address, l.city, l.zip, l.lat, l.lng, l.price, l.bedrooms,
            l.bathrooms, l.sqft, l.yearBuilt, l.propertyType, l.active, l.firstSeen?.toString(), l.lastSeen?.toString())
    }
}

object AnalysisDto {
    fun from(a: Analysis): JsonObject = buildJsonObject {
        put("listingId", a.listingId)
        put("profileId", a.profileId)
        put("runAt", a.runAt?.toString())
        put("netMonthlyCost", a.netMonthlyCost)
        put("cashToClose", a.cashToClose)
        put("lenderPass", a.lenderPass)
        put("selfSufficiencyRatio", a.selfSufficiencyRatio)
        put("dti", a.dti)
        put("driveMinutes", a.driveMinutes)
        put("breakdown", json.parseToJsonElement(a.breakdownJson))
        put("programResults", json.parseToJsonElement(a.programResultsJson))
    }
}
