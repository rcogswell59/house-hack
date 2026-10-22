package io.initialcapacity.househack.web

import io.initialcapacity.househack.analyzer.AnalyzeListingTask
import io.initialcapacity.househack.analyzer.AnalyzeListingWorker
import io.initialcapacity.househack.analyzer.ListingAnalyzer
import io.initialcapacity.househack.analyzer.ProgramRuleEngine
import io.initialcapacity.househack.analyzer.StaleAnalysisWorkFinder
import io.initialcapacity.househack.collector.CollectListingsWorker
import io.initialcapacity.househack.collector.FetchRentEstimatesWorker
import io.initialcapacity.househack.parcels.FloridaCadastralClient
import io.initialcapacity.househack.parcels.ImportParcelsWorker
import io.initialcapacity.househack.parcels.ParcelSource
import io.initialcapacity.househack.rentcast.RentCastClient
import io.initialcapacity.workflow.CollectorTask
import io.initialcapacity.workflow.DailyWorkFinder
import io.initialcapacity.workflow.WorkScheduler
import io.initialcapacity.workflow.Worker
import java.time.Clock
import java.time.ZoneOffset

/**
 * Builds the schedulers each process runs. Locally each app runs one set; on Render the web process runs both
 * (RUN_WORKERS=collector,analyzer) so the free tier needs a single service.
 */
object Workers {
    const val COLLECTOR = "collector"
    const val ANALYZER = "analyzer"
    const val COUNTY = "Brevard"

    fun start(
        deps: Dependencies,
        names: Set<String>,
        rentCast: RentCastClient,
        clock: Clock = Clock.systemUTC(),
        parcelSource: ParcelSource = FloridaCadastralClient(),
    ): List<WorkScheduler<*>> = names.map { name ->
        when (name) {
            COLLECTOR -> collector(deps, rentCast, clock, parcelSource)
            ANALYZER -> analyzer(deps, clock)
            else -> throw IllegalArgumentException("unknown worker set '$name'; expected $COLLECTOR or $ANALYZER")
        }.also { it.start() }
    }

    fun collector(deps: Dependencies, rentCast: RentCastClient, clock: Clock, parcelSource: ParcelSource): WorkScheduler<CollectorTask> {
        val workers: MutableList<Worker<CollectorTask>> = mutableListOf(
            CollectListingsWorker(rentCast, deps.listings, deps.budget, deps.profiles, clock),
            ImportParcelsWorker(parcelSource, deps.parcels, deps.listings, deps.profiles),
            FetchRentEstimatesWorker(rentCast, deps.listings, deps.rents, deps.parcels, deps.budget, clock),
        )
        return WorkScheduler(DailyWorkFinder(clock), workers, delay = 3600)
    }

    fun analyzer(deps: Dependencies, clock: Clock): WorkScheduler<AnalyzeListingTask> {
        val year = clock.instant().atZone(ZoneOffset.UTC).year
        val engine = ProgramRuleEngine(deps.programRules.forYear(year, COUNTY).ifEmpty { deps.programRules.forYear(2026, COUNTY) })
        val worker = AnalyzeListingWorker(ListingAnalyzer(engine), deps.listings, deps.parcels, deps.rents, deps.profiles, deps.analyses)
        return WorkScheduler(StaleAnalysisWorkFinder(deps.listings, deps.profiles), mutableListOf(worker), delay = 60)
    }
}
