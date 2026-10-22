package test.initialcapacity.househack.parcels

import io.initialcapacity.househack.parcels.Envelope
import io.initialcapacity.househack.parcels.FloridaCadastralClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FloridaCadastralClientTest {
    private val duplexJson = """
        {"objectIdFieldName":"OBJECTID","features":[
          {"attributes":{"PARCEL_ID":"24 3621-BM-*-5.01","DOR_UC":"008","JV":649180,"AV_SD":520350,"TV_SD":495350,"NO_RES_UNT":2,
                         "PHY_ADDR1":"1209 N INDIAN RIVER DR","PHY_CITY":"COCOA","PHY_ZIPCD":32922,"ACT_YR_BLT":1901,"TAX_AUTH_C":"23D0"}},
          {"attributes":{"PARCEL_ID":"24 3621-50-*-10","DOR_UC":"008","JV":668110,"AV_SD":315230,"TV_SD":285230,"NO_RES_UNT":2,
                         "PHY_ADDR1":"1141 N INDIAN RIVER DR","PHY_CITY":"COCOA","PHY_ZIPCD":null,"ACT_YR_BLT":null,"TAX_AUTH_C":"23D0"}}
        ]}
    """.trimIndent()

    private val requests = mutableListOf<HttpRequestData>()

    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK) = FloridaCadastralClient(
        engine = MockEngine { request -> requests += request; respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
    )

    @Test
    fun multiFamilyParcels_queries_envelope_with_dor_filter_and_maps_fields() = runBlocking {
        val facts = client(duplexJson).multiFamilyParcels(Envelope(minLng = -80.80, minLat = 28.30, maxLng = -80.70, maxLat = 28.40))

        val url = requests.single().url
        assertTrue(url.encodedPath.endsWith("/FeatureServer/0/query"), url.encodedPath)
        assertEquals("DOR_UC='008'", url.parameters["where"])
        assertEquals("esriGeometryEnvelope", url.parameters["geometryType"])
        assertEquals("4326", url.parameters["inSR"])
        assertEquals("false", url.parameters["returnGeometry"])
        assertTrue(url.parameters["geometry"]!!.contains("\"xmin\":-80.8"), url.parameters["geometry"]!!)

        assertEquals(2, facts.size)
        val first = facts[0]
        assertEquals("24 3621-BM-*-5.01", first.parcelId)
        assertEquals("1209 N INDIAN RIVER DR", first.addressNorm)
        assertEquals("COCOA", first.city)
        assertEquals("32922", first.zip)
        assertEquals(649_180.0, first.justValue)
        assertEquals(520_350.0, first.assessedValue)
        assertEquals(2, first.units)
        assertEquals("008", first.landUseCode)
        assertEquals(1901, first.yearBuilt)
        assertNull(first.millage)
        assertNull(facts[1].zip)
        assertNull(facts[1].yearBuilt)
    }

    @Test
    fun parcelAt_sends_point_geometry_and_returns_single_or_null() = runBlocking {
        val hit = client(duplexJson).parcelAt(lat = 28.3627, lng = -80.7276)
        val miss = client("""{"features":[]}""").parcelAt(lat = 28.0, lng = -80.0)

        val url = requests.first().url
        assertEquals("esriGeometryPoint", url.parameters["geometryType"])
        assertEquals("1=1", url.parameters["where"])
        assertTrue(url.parameters["geometry"]!!.contains("\"x\":-80.7276"))
        assertEquals("24 3621-BM-*-5.01", hit?.parcelId)
        assertNull(miss)
    }

    @Test
    fun arcgis_error_body_throws() {
        val e = runCatching {
            runBlocking { client("""{"error":{"code":400,"message":"Cannot perform query.","details":[]}}""").parcelAt(28.0, -80.0) }
        }.exceptionOrNull()
        assertTrue(e is IllegalStateException && e.message!!.contains("Cannot perform query"), "$e")
    }

    @Test
    fun envelope_tiles_cover_the_area_in_steps() {
        val tiles = Envelope(-80.80, 28.30, -80.65, 28.45).tiles(0.1)

        assertEquals(4, tiles.size)
        assertEquals(Envelope(-80.80, 28.30, -80.70, 28.40), tiles[0])
        assertTrue(tiles.all { it.maxLng <= -80.65 + 1e-9 && it.maxLat <= 28.45 + 1e-9 })
    }

    @Test
    fun envelope_around_point_uses_miles() {
        val e = Envelope.around(lat = 28.3922, lng = -80.6077, radiusMiles = 30.0)

        assertEquals(28.3922 - 30.0 / 69.0, e.minLat, 1e-6)
        assertEquals(28.3922 + 30.0 / 69.0, e.maxLat, 1e-6)
        assertTrue(e.minLng < -80.6077 && e.maxLng > -80.6077)
        assertEquals(e.maxLng - (-80.6077), (-80.6077) - e.minLng, 1e-9)
    }
}
