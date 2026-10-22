package io.initialcapacity.househack.listings

import java.time.Instant

data class ParcelFact(
    val parcelId: String,
    val addressNorm: String,
    val city: String?,
    val zip: String?,
    val justValue: Double?,
    val assessedValue: Double?,
    val taxesAnnual: Double?,
    val millage: Double?,
    val yearBuilt: Int?,
    val units: Int?,
    val landUseCode: String?,
    val importedAt: Instant?,
)
