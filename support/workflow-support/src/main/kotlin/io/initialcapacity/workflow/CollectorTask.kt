package io.initialcapacity.workflow

import java.time.LocalDate

/** One run of a named daily job. */
data class CollectorTask(val name: String, val runDate: LocalDate)
