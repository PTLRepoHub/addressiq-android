package com.addressiq.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server reads device integrity at exact key paths: the scoring engine at
 * `rawPayload.device.isEmulator` and `rawPayload.location.isMocked`, the
 * dashboard at those plus `rawPayload.security.*`. A renamed key is not a
 * compile error on either side, it is a fraud check that silently stops firing.
 */
class DeviceSignalsEnvelopeTest {

    private fun envelope(signals: Map<String, Map<String, Any>>): JsonObject =
        Json.parseToJsonElement(
            AddressIQ.buildTransitEventJson(
                eventId = "iqevt_android_test",
                locationCode = "loc_abc123",
                eventType = "GEOFENCE_ENTER",
                lat = 6.5001,
                lon = 3.3501,
                accuracyM = 12.5,
                deviceTs = "2026-08-25T07:14:45.000Z",
                deviceSignals = signals,
            ),
        ).jsonObject

    @Test
    fun `carries device integrity at the paths the server reads`() {
        val json = envelope(
            mapOf(
                "device" to mapOf("isEmulator" to true),
                "location" to mapOf("isMocked" to true),
                "security" to mapOf(
                    "isRooted" to true,
                    "hasSpoofingApps" to true,
                    "spoofingAppsFound" to listOf("com.lexa.fakegps"),
                ),
            ),
        )
        val raw = json["rawPayload"]!!.jsonObject

        assertTrue(raw["device"]!!.jsonObject["isEmulator"]!!.jsonPrimitive.boolean)
        assertTrue(raw["location"]!!.jsonObject["isMocked"]!!.jsonPrimitive.boolean)
        val security = raw["security"]!!.jsonObject
        assertTrue(security["isRooted"]!!.jsonPrimitive.boolean)
        assertTrue(security["hasSpoofingApps"]!!.jsonPrimitive.boolean)
        assertEquals(
            "com.lexa.fakegps",
            security["spoofingAppsFound"]!!.jsonArray[0].jsonPrimitive.content,
        )
    }

    @Test
    fun `keeps the fields ingest requires alongside the signals`() {
        val json = envelope(mapOf("device" to mapOf("isEmulator" to false)))

        assertEquals("loc_abc123", json["locationId"]!!.jsonPrimitive.content)
        assertEquals("GEOFENCE_ENTER", json["eventType"]!!.jsonPrimitive.content)
        assertEquals("ANDROID", json["deviceOs"]!!.jsonPrimitive.content)
    }

    @Test
    fun `omits rawPayload entirely when nothing was collected`() {
        // An absent payload must stay absent: the dashboard tells "no device
        // data" apart from "checked and clean" by its presence.
        val json = envelope(emptyMap())
        assertFalse(json.containsKey("rawPayload"))
    }

    @Test
    fun `reports a null fix as not mocked rather than guessing`() {
        assertFalse(DeviceSignals.isMocked(null))
    }
}
