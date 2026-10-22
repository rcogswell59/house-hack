package io.initialcapacity.househack.parcels

import io.initialcapacity.househack.listings.AddressNormalizer
import io.initialcapacity.househack.listings.ParcelFact
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Florida DOR statewide cadastral layer (county tax rolls joined to parcel polygons).
 * Spatial queries only: attribute-only scans of this 10.8M-row layer time out. See docs/course/research/parcel-data-source.md.
 */
class FloridaCadastralClient(
    engine: HttpClientEngine = CIO.create(),
    private val baseUrl: String = DEFAULT_URL,
) : ParcelSource {

    private val http = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = 120_000 }
    }

    override suspend fun multiFamilyParcels(envelope: Envelope): List<ParcelFact> = query(
        where = "DOR_UC='$MULTI_FAMILY_UNDER_10'",
        geometryType = "esriGeometryEnvelope",
        geometry = """{"xmin":${envelope.minLng},"ymin":${envelope.minLat},"xmax":${envelope.maxLng},"ymax":${envelope.maxLat},"spatialReference":{"wkid":4326}}""",
    )

    override suspend fun parcelAt(lat: Double, lng: Double): ParcelFact? = query(
        where = "1=1",
        geometryType = "esriGeometryPoint",
        geometry = """{"x":$lng,"y":$lat,"spatialReference":{"wkid":4326}}""",
    ).firstOrNull()

    private suspend fun query(where: String, geometryType: String, geometry: String): List<ParcelFact> {
        val response = http.get("$baseUrl/query") {
            parameter("where", where)
            parameter("geometry", geometry)
            parameter("geometryType", geometryType)
            parameter("inSR", "4326")
            parameter("spatialRel", "esriSpatialRelIntersects")
            parameter("outFields", OUT_FIELDS)
            parameter("returnGeometry", "false")
            parameter("resultRecordCount", "2000")
            parameter("f", "json")
        }
        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.let { throw IllegalStateException("ArcGIS error: ${it.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: it}") }

        return root["features"]?.jsonArray?.mapNotNull { toParcelFact(it.jsonObject["attributes"]!!.jsonObject) } ?: emptyList()
    }

    companion object {
        const val DEFAULT_URL = "https://services9.arcgis.com/Gh9awoU677aKree0/arcgis/rest/services/Florida_Statewide_Cadastral/FeatureServer/0"
        const val MULTI_FAMILY_UNDER_10 = "008"
        private const val OUT_FIELDS = "PARCEL_ID,DOR_UC,JV,AV_SD,TV_SD,NO_RES_UNT,PHY_ADDR1,PHY_CITY,PHY_ZIPCD,ACT_YR_BLT,TAX_AUTH_C"
        private val json = Json { ignoreUnknownKeys = true }

        fun toParcelFact(a: JsonObject): ParcelFact? {
            val parcelId = a.string("PARCEL_ID") ?: return null
            val address = a.string("PHY_ADDR1") ?: ""
            return ParcelFact(
                parcelId = parcelId,
                addressNorm = AddressNormalizer.normalize(address),
                city = a.string("PHY_CITY"),
                zip = a.int("PHY_ZIPCD")?.toString() ?: a.string("PHY_ZIPCD"),
                justValue = a.double("JV"),
                assessedValue = a.double("AV_SD"),
                taxesAnnual = null,   // not in the layer
                millage = null,       // TAX_AUTH_C is a code; millage lookup is future work
                yearBuilt = a.int("ACT_YR_BLT")?.takeIf { it > 0 },
                units = a.int("NO_RES_UNT"),
                landUseCode = a.string("DOR_UC"),
                importedAt = null,
            )
        }

        private fun JsonObject.primitive(key: String) = this[key] as? JsonPrimitive
        private fun JsonObject.string(key: String) = primitive(key)?.takeUnless { it.contentOrNull == null }?.content
        private fun JsonObject.double(key: String) = primitive(key)?.doubleOrNull
        private fun JsonObject.int(key: String) = primitive(key)?.intOrNull
    }
}
