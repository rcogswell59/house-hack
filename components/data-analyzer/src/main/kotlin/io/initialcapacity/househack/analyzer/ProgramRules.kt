package io.initialcapacity.househack.analyzer

import io.initialcapacity.househack.db.DatabaseTemplate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** One row of `program_rules`: a program's parameters for a year and county. */
data class ProgramRule(val program: String, val year: Int, val county: String, val params: JsonObject)

class ProgramRuleRepository(private val db: DatabaseTemplate) {
    fun forYear(year: Int, county: String): List<ProgramRule> = db.query(
        "select program, year, county, params::text from program_rules where year = ? and county = ? order by program", year, county,
    ) { rs -> ProgramRule(rs.getString(1), rs.getInt(2), rs.getString(3), Json.parseToJsonElement(rs.getString(4)).jsonObject) }
}

@Serializable
data class ProgramResult(
    val program: String,
    val eligible: Boolean,
    val assistance: Double,
    val reasons: List<String>,
    val exclusiveGroup: String?,
)

@Serializable
data class ProgramOutcome(val results: List<ProgramResult>, val totalAssistance: Double)
