package com.addressiq.android

import com.addressiq.android.network.JsonAny
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The HTTP layer speaks `Map<String, Any?>` in both directions, and the API
 * puts real JSON types on the wire — `"isExisting": false`, `"slaHours": 24`.
 *
 * A previous adapter modelled every body as `Map<String, String?>`, so
 * `startVerification` died on the digital-verification response with
 * "Unexpected JSON token at offset 257 ... at path: $['isExisting']", and
 * request bodies went out with `"startDigital": "true"`. These tests pin both
 * halves of the round trip.
 */
class JsonAnyTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `decodes the digital verification response, booleans included`() {
        val raw = """
            {"verificationCode":"VER_123","locationCode":"LOC_EIMZ3AK8C5XRZBV2",
             "id":"9f0e3-e77b4498235a","isExisting":false}
        """.trimIndent()

        val parsed = JsonAny.decodeObject(json, raw)

        assertEquals("VER_123", parsed["verificationCode"])
        assertEquals("LOC_EIMZ3AK8C5XRZBV2", parsed["locationCode"])
        assertEquals(false, parsed["isExisting"])
    }

    @Test
    fun `decodes numbers, nulls, nested objects and arrays`() {
        val raw = """
            {"slaHours":24,"score":0.75,"agentId":null,
             "geofence":{"radiusM":150,"active":true},"providers":["internal_ai","partner"]}
        """.trimIndent()

        val parsed = JsonAny.decodeObject(json, raw)

        assertEquals(24L, parsed["slaHours"])
        assertEquals(0.75, parsed["score"])
        assertNull(parsed["agentId"])
        assertEquals(mapOf("radiusM" to 150L, "active" to true), parsed["geofence"])
        assertEquals(listOf("internal_ai", "partner"), parsed["providers"])
    }

    @Test
    fun `decodes an array body of objects`() {
        val raw = """[{"type":"digital","enabled":true},{"type":"physical","enabled":false}]"""

        val parsed = JsonAny.decodeObjectArray(json, raw)

        assertEquals(2, parsed.size)
        assertEquals(true, parsed[0]["enabled"])
        assertEquals("physical", parsed[1]["type"])
    }

    @Test
    fun `encodes booleans and numbers as JSON types, not strings`() {
        val body = mapOf<String, Any?>(
            "physicalProvider" to "partner",
            "startDigital" to true,
            "slaHours" to 24,
            "agentId" to null,
        )

        val encoded = JsonAny.toJson(body).toString()

        assertEquals(
            """{"physicalProvider":"partner","startDigital":true,"slaHours":24,"agentId":null}""",
            encoded,
        )
    }
}
