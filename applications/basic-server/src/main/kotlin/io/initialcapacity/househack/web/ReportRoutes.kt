package io.initialcapacity.househack.web

import io.initialcapacity.househack.analyzer.Breakdown
import io.initialcapacity.househack.analyzer.ListingAnalyzer
import io.initialcapacity.househack.analyzer.ProgramOutcome
import io.initialcapacity.househack.listings.Analysis
import io.initialcapacity.househack.listings.Listing
import io.ktor.server.application.call
import io.ktor.server.freemarker.FreeMarkerContent
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.decodeFromString
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun Route.reportRoutes(deps: Dependencies) {
    get("/report") {
        val profile = deps.profiles.findFirst()
        if (profile == null) {
            call.respond(FreeMarkerContent("report.ftl", mapOf("hasProfile" to false, "rows" to emptyList<Any>(), "pendingCount" to 0)))
            return@get
        }
        val rows = deps.analyses.rankedForProfile(profile.id!!).mapIndexed { i, (analysis, listing) -> ReportRow.of(i + 1, analysis, listing) }
        call.respond(FreeMarkerContent("report.ftl", mapOf(
            "hasProfile" to true,
            "profileName" to profile.name,
            "rows" to rows,
            "pendingCount" to deps.analyses.countPendingForProfile(profile.id!!),
            "activeCount" to deps.listings.findActive().size,
        )))
    }

    get("/listings/{id}") {
        val id = call.parameters["id"]?.toLongOrNull() ?: throw NotFoundException("listing not found")
        val listing = deps.listings.findById(id) ?: throw NotFoundException("listing $id not found")
        val profile = deps.profiles.findFirst()
        val analysis = profile?.id?.let { deps.analyses.findForListing(id, it) }
        val parcel = deps.parcels.findForListing(id)
        val rent = deps.rents.findForListing(id)

        call.respond(FreeMarkerContent("listing.ftl", mapOf(
            "listing" to listing,
            "price" to Fmt.money0(listing.price),
            "parcel" to parcel,
            "parcelJustValue" to (parcel?.justValue?.let(Fmt::money0) ?: "—"),
            "rent" to rent,
            "rentPerUnit" to (rent?.estRentPerUnit?.let(Fmt::money0) ?: "—"),
            "analysis" to analysis,
            "detail" to analysis?.let { AnalysisDetail.of(it) },
        )))
    }
}

data class ReportRow(
    val rank: Int, val listingId: Long, val address: String, val city: String, val price: String, val units: Int,
    val netMonthlyCost: String, val cashToClose: String, val lenderPass: Boolean, val driveMinutes: String,
) {
    companion object {
        fun of(rank: Int, a: Analysis, l: Listing): ReportRow {
            val breakdown = ListingAnalyzer.json.decodeFromString<Breakdown>(a.breakdownJson)
            return ReportRow(rank, l.id!!, l.address, l.city ?: "", Fmt.money0(l.price), breakdown.units,
                Fmt.money0(a.netMonthlyCost), Fmt.money0(a.cashToClose), a.lenderPass, "%.0f".format(a.driveMinutes))
        }
    }
}

data class AnalysisDetail(
    val runAt: String,
    val netMonthlyCost: String, val cashToClose: String, val driveMinutes: String,
    val units: Int, val unitsSource: String, val millage: String, val millageSource: String,
    val buyer: List<Pair<String, String>>,
    val lender: List<Pair<String, String>>,
    val lenderPass: Boolean,
    val programs: List<ProgramLine>,
    val totalAssistance: String,
) {
    data class ProgramLine(val program: String, val eligible: Boolean, val assistance: String, val reasons: List<String>)

    companion object {
        fun of(a: Analysis): AnalysisDetail {
            val b = ListingAnalyzer.json.decodeFromString<Breakdown>(a.breakdownJson)
            val p = ListingAnalyzer.json.decodeFromString<ProgramOutcome>(a.programResultsJson)
            return AnalysisDetail(
                runAt = a.runAt?.atZone(ZoneOffset.UTC)?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: "",
                netMonthlyCost = Fmt.money2(a.netMonthlyCost), cashToClose = Fmt.money2(a.cashToClose), driveMinutes = "%.0f".format(a.driveMinutes),
                units = b.units, unitsSource = b.unitsSource, millage = "%.4f".format(b.millage), millageSource = b.millageSource,
                buyer = listOf(
                    "Loan amount (incl. upfront MIP)" to Fmt.money2(b.buyer.loanAmount),
                    "Principal & interest" to Fmt.money2(b.buyer.principalAndInterest),
                    "Monthly MIP" to Fmt.money2(b.buyer.monthlyMip),
                    "Property taxes (homestead on your unit)" to Fmt.money2(b.buyer.monthlyTaxes),
                    "Insurance" to Fmt.money2(b.buyer.monthlyInsurance),
                    "Vacancy + maintenance reserves" to Fmt.money2(b.buyer.reserves),
                    "Rent from other units (100%)" to "− " + Fmt.money2(b.buyer.otherUnitsRent),
                    "Net monthly housing cost" to Fmt.money2(b.buyer.netMonthlyCost),
                    "Cash to close (after assistance)" to Fmt.money2(b.buyer.cashToClose),
                ),
                lender = listOfNotNull(
                    "PITI" to Fmt.money2(b.lender.piti),
                    "Qualifying rent (75% of other units)" to Fmt.money2(b.lender.qualifyingRent),
                    "Debt-to-income" to "%.1f%% (%s)".format(b.lender.dti * 100, if (b.lender.dtiPass) "pass" else "fail"),
                    b.lender.selfSufficiencyRatio?.let { "Self-sufficiency ratio (3–4 units)" to "%.2f (%s)".format(it, if (b.lender.selfSufficiencyPass == true) "pass" else "fail") },
                ),
                lenderPass = b.lender.pass,
                programs = p.results.map { ProgramLine(it.program, it.eligible, Fmt.money0(it.assistance), it.reasons) },
                totalAssistance = Fmt.money0(p.totalAssistance),
            )
        }
    }
}

object Fmt {
    fun money0(v: Double) = "$" + "%,.0f".format(v)
    fun money2(v: Double) = "$" + "%,.2f".format(v)
}
