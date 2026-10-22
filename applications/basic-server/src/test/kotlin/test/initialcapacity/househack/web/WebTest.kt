package test.initialcapacity.househack.web

import io.initialcapacity.househack.listings.Analysis
import io.initialcapacity.househack.listings.Listing
import io.initialcapacity.househack.profile.BuyerProfile
import io.initialcapacity.househack.web.Dependencies
import io.initialcapacity.househack.web.module
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import test.initialcapacity.househack.db.TestDatabase
import java.time.Instant

/** Shared harness: real test database, routes mounted exactly as production does, JSON-capable client. */
object WebTest {
    val deps by lazy { Dependencies(TestDatabase.dataSource) }
    val t0: Instant = Instant.parse("2026-10-01T12:00:00Z")

    fun run(block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit) = testApplication {
        application { module(deps) }
        val client = createClient { install(ContentNegotiation) { json() } }
        block(client)
    }

    fun seedProfile(): BuyerProfile = deps.profiles.save(BuyerProfile.defaults())

    fun seedListing(sourceId: String, address: String, price: Double = 400_000.0): Listing = deps.listings.upsertSeen(
        Listing(
            null, "rentcast", sourceId, address, "Cocoa", "32922", 28.3861, -80.7420, price, 4, 2.0, 1800, 1975, "Multi-Family",
            null, null, null, true, null, """{"id":"$sourceId"}""",
        ),
        t0,
    )

    fun seedAnalysis(listing: Listing, profile: BuyerProfile, netMonthlyCost: Double, lenderPass: Boolean = true): Analysis = deps.analyses.upsert(
        Analysis(
            null, listing.id!!, profile.id!!, null, netMonthlyCost, 11_000.0, lenderPass, null, 0.22, 22.9,
            breakdownJson = """{"units":2,"unitsSource":"parcel","millage":0.016,"millageSource":"parcel","rentPerUnit":1800.0,
              "buyer":{"loanAmount":392755.0,"principalAndInterest":2482.5,"monthlyMip":180.0,"monthlyTaxes":466.67,"monthlyInsurance":400.0,
                       "reserves":234.0,"otherUnitsRent":1800.0,"netMonthlyCost":$netMonthlyCost,"cashToClose":11000.0},
              "lender":{"piti":3529.2,"qualifyingRent":1350.0,"dti":0.222,"dtiPass":true,"selfSufficiencyRatio":null,"selfSufficiencyPass":null,"pass":$lenderPass}}""",
            programResultsJson = """{"results":[{"program":"BREVARD_HFA","eligible":true,"assistance":15000.0,"reasons":["0% deferred second mortgage"],"exclusiveGroup":"first_mortgage_dpa"}],"totalAssistance":15000.0}""",
        ),
    )
}
