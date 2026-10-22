package io.initialcapacity.househack.web

import io.initialcapacity.househack.profile.BuyerProfile
import io.initialcapacity.househack.profile.ProfileValidator
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.freemarker.FreeMarkerContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.profileRoutes(deps: Dependencies) {
    get("/profile") {
        val profile = deps.profiles.findFirst() ?: BuyerProfile.defaults()
        call.respond(FreeMarkerContent("profile.ftl", mapOf("form" to ProfileForm.from(profile), "errors" to emptyList<String>(), "saved" to (profile.id != null))))
    }

    post("/profile") {
        val params = call.receiveParameters()
        val existing = deps.profiles.findFirst()
        val (profile, parseErrors) = ProfileForm.parse(params, existingId = existing?.id)
        val errors = parseErrors + (profile?.let { ProfileValidator.validate(it) } ?: emptyList())

        if (profile == null || errors.isNotEmpty()) {
            call.respond(HttpStatusCode.BadRequest, FreeMarkerContent("profile.ftl", mapOf("form" to ProfileForm.echo(params), "errors" to errors, "saved" to (existing != null))))
            return@post
        }

        deps.profiles.save(profile)
        call.response.headers.append(HttpHeaders.Location, "/report")
        call.respond(HttpStatusCode.SeeOther)
    }
}

/** Form field names match [BuyerProfile] property names; checkboxes arrive as "on" or are absent. */
object ProfileForm {
    val numericFields = listOf(
        "annualIncome", "liquidSavings", "creditScore", "householdSize", "otherMonthlyDebts", "workplaceLat", "workplaceLng",
        "maxDriveMinutes", "unitsMin", "unitsMax", "vacancyRate", "maintenanceRate", "insuranceRate", "closingCostRate",
        "mortgageRate", "roadFactor", "avgSpeedMph",
    )
    val checkboxFields = listOf("firstTimeBuyer", "hthEligibleOccupation", "veteran")

    fun from(p: BuyerProfile): Map<String, String> = mapOf(
        "name" to p.name, "annualIncome" to plain(p.annualIncome), "liquidSavings" to plain(p.liquidSavings), "creditScore" to "${p.creditScore}",
        "firstTimeBuyer" to flag(p.firstTimeBuyer), "hthEligibleOccupation" to flag(p.hthEligibleOccupation), "veteran" to flag(p.veteran),
        "householdSize" to "${p.householdSize}", "otherMonthlyDebts" to plain(p.otherMonthlyDebts),
        "workplaceLat" to "${p.workplaceLat}", "workplaceLng" to "${p.workplaceLng}", "maxDriveMinutes" to "${p.maxDriveMinutes}",
        "unitsMin" to "${p.unitsMin}", "unitsMax" to "${p.unitsMax}", "vacancyRate" to "${p.vacancyRate}", "maintenanceRate" to "${p.maintenanceRate}",
        "insuranceRate" to "${p.insuranceRate}", "closingCostRate" to "${p.closingCostRate}", "mortgageRate" to "${p.mortgageRate}",
        "roadFactor" to "${p.roadFactor}", "avgSpeedMph" to plain(p.avgSpeedMph),
    )

    fun echo(params: Parameters): Map<String, String> =
        (listOf("name") + numericFields).associateWith { params[it] ?: "" } + checkboxFields.associateWith { flag(params[it] != null) }

    fun parse(params: Parameters, existingId: Long?): Pair<BuyerProfile?, List<String>> {
        val errors = mutableListOf<String>()
        fun num(field: String): Double? = params[field]?.trim()?.let { raw ->
            raw.replace(",", "").toDoubleOrNull() ?: run { errors += "${label(field)} must be a number."; null }
        } ?: run { errors += "${label(field)} is required."; null }
        fun int(field: String): Int? = num(field)?.let { if (it % 1.0 == 0.0) it.toInt() else run { errors += "${label(field)} must be a whole number."; null } }
        fun flag(field: String) = params[field] != null

        val values = numericFields.associateWith { if (it in INT_FIELDS) int(it) else num(it) }
        val name = params["name"]?.trim().orEmpty()
        if (errors.isNotEmpty()) return null to errors

        return BuyerProfile(
            id = existingId,
            name = name,
            annualIncome = values.getValue("annualIncome")!!.toDouble(),
            liquidSavings = values.getValue("liquidSavings")!!.toDouble(),
            creditScore = values.getValue("creditScore")!!.toInt(),
            firstTimeBuyer = flag("firstTimeBuyer"),
            hthEligibleOccupation = flag("hthEligibleOccupation"),
            veteran = flag("veteran"),
            householdSize = values.getValue("householdSize")!!.toInt(),
            otherMonthlyDebts = values.getValue("otherMonthlyDebts")!!.toDouble(),
            workplaceLat = values.getValue("workplaceLat")!!.toDouble(),
            workplaceLng = values.getValue("workplaceLng")!!.toDouble(),
            maxDriveMinutes = values.getValue("maxDriveMinutes")!!.toInt(),
            unitsMin = values.getValue("unitsMin")!!.toInt(),
            unitsMax = values.getValue("unitsMax")!!.toInt(),
            vacancyRate = values.getValue("vacancyRate")!!.toDouble(),
            maintenanceRate = values.getValue("maintenanceRate")!!.toDouble(),
            insuranceRate = values.getValue("insuranceRate")!!.toDouble(),
            closingCostRate = values.getValue("closingCostRate")!!.toDouble(),
            mortgageRate = values.getValue("mortgageRate")!!.toDouble(),
            roadFactor = values.getValue("roadFactor")!!.toDouble(),
            avgSpeedMph = values.getValue("avgSpeedMph")!!.toDouble(),
        ) to errors
    }

    private val INT_FIELDS = setOf("creditScore", "householdSize", "maxDriveMinutes", "unitsMin", "unitsMax")
    private fun flag(b: Boolean) = if (b) "on" else ""
    private fun plain(d: Double) = if (d % 1.0 == 0.0) "${d.toLong()}" else "$d"
    private fun label(field: String) = field.replace(Regex("([A-Z])"), " $1").lowercase().replaceFirstChar { it.uppercase() }
}
