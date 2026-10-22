package io.initialcapacity.househack.listings

/** Reduces a street address to a comparable key so listings can be matched to county parcels. */
object AddressNormalizer {
    private val abbreviations = mapOf(
        "STREET" to "ST", "AVENUE" to "AVE", "ROAD" to "RD", "DRIVE" to "DR", "BOULEVARD" to "BLVD",
        "COURT" to "CT", "LANE" to "LN", "PLACE" to "PL", "TERRACE" to "TER", "CIRCLE" to "CIR", "HIGHWAY" to "HWY",
        "NORTH" to "N", "SOUTH" to "S", "EAST" to "E", "WEST" to "W",
    )

    fun normalize(street: String): String = street
        .substringBefore(',')
        .uppercase()
        .replace(Regex("[^A-Z0-9 ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .map { abbreviations[it] ?: it }
        .joinToString(" ")
}
