package io.initialcapacity.househack.analyzer

import io.initialcapacity.househack.listings.Analysis
import io.initialcapacity.househack.listings.Listing
import io.initialcapacity.househack.listings.ParcelFact
import io.initialcapacity.househack.listings.RentEstimate
import io.initialcapacity.househack.profile.BuyerProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.math.ceil

@Serializable
data class Breakdown(
    val units: Int,
    val unitsSource: String,
    val millage: Double,
    val millageSource: String,
    val rentPerUnit: Double,
    val buyer: BuyerView,
    val lender: LenderView,
)

/** Joins one listing with its parcel, rent estimate, and the buyer's profile; returns null (with a logged reason) when it can't be scored. */
class ListingAnalyzer(private val engine: ProgramRuleEngine, private val defaultMillage: Double = DEFAULT_MILLAGE) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    fun analyze(profile: BuyerProfile, listing: Listing, parcel: ParcelFact?, rent: RentEstimate?): Analysis? {
        val lat = listing.lat
        val lng = listing.lng
        if (lat == null || lng == null) return skip(listing, "no coordinates")
        if (rent == null) return skip(listing, "no rent estimate yet")

        val driveMinutes = DriveTimeEstimator.minutes(lat, lng, profile.workplaceLat, profile.workplaceLng, profile.roadFactor, profile.avgSpeedMph)
        if (driveMinutes > profile.maxDriveMinutes) return skip(listing, "drive ${"%.0f".format(driveMinutes)} min exceeds ${profile.maxDriveMinutes}")

        val parcelUnits = parcel?.units
        val bedrooms = listing.bedrooms
        val (units, unitsSource) = when {
            parcelUnits != null -> parcelUnits to "parcel"
            bedrooms != null -> ceil(bedrooms / 2.0).toInt() to "bedrooms_fallback"
            else -> return skip(listing, "unit count unknown")
        }
        if (units !in profile.unitsMin..profile.unitsMax) return skip(listing, "$units units outside ${profile.unitsMin}-${profile.unitsMax}")

        val (millage, millageSource) = parcel?.millage?.let { it to "parcel" } ?: (defaultMillage to "default")

        val base = FinancingInputs(
            price = listing.price, units = units, rentPerUnit = rent.estRentPerUnit, millage = millage,
            mortgageRate = profile.mortgageRate, insuranceRate = profile.insuranceRate, vacancyRate = profile.vacancyRate,
            maintenanceRate = profile.maintenanceRate, closingCostRate = profile.closingCostRate, monthlyIncome = profile.monthlyIncome,
            otherMonthlyDebts = profile.otherMonthlyDebts, assistanceTotal = 0.0,
        )
        val loanAmount = FinancingCalculator.buyerView(base).loanAmount
        val programs = engine.evaluate(profile, listing.price, units, loanAmount)
        val inputs = base.copy(assistanceTotal = programs.totalAssistance)
        val buyer = FinancingCalculator.buyerView(inputs)
        val lender = FinancingCalculator.lenderView(inputs)

        return Analysis(
            id = null,
            listingId = listing.id ?: throw IllegalArgumentException("listing must be persisted"),
            profileId = profile.id ?: throw IllegalArgumentException("profile must be persisted"),
            runAt = null,
            netMonthlyCost = buyer.netMonthlyCost,
            cashToClose = buyer.cashToClose,
            lenderPass = lender.pass,
            selfSufficiencyRatio = lender.selfSufficiencyRatio,
            dti = lender.dti,
            driveMinutes = driveMinutes,
            breakdownJson = json.encodeToString(Breakdown(units, unitsSource, millage, millageSource, rent.estRentPerUnit, buyer, lender)),
            programResultsJson = json.encodeToString(programs),
        )
    }

    private fun skip(listing: Listing, reason: String): Analysis? {
        logger.info("skipping listing {} ({}): {}", listing.id, listing.address, reason)
        return null
    }

    companion object {
        /** Brevard County combined millage is roughly 16 mills; used only when the parcel has none. */
        const val DEFAULT_MILLAGE = 0.016
        val json = Json { prettyPrint = false }
    }
}
