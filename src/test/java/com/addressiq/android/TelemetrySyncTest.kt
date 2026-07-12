package com.addressiq.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM (Android-framework-free) coverage for the transit-event telemetry
 * pipeline: the pure event serializer and the empty-queue sync guard. The
 * SQLCipher-backed drain path is exercised in instrumented tests.
 */
class TelemetrySyncTest {

    @Test
    fun syncReturnsZeroWhenQueueUninitialized() = runBlocking {
        // No AddressIQTelemetryQueue.init(...) has run in this JVM, so
        // sync() must no-op to 0 rather than throw.
        assertEquals(0, AddressIQ.sync())
    }

    @Test
    fun buildTransitEventJsonMatchesEnvelopeShape() {
        val json = AddressIQ.buildTransitEventJson(
            eventId = "iqevt_android_abc123",
            locationCode = "LOC-42",
            eventType = "GEOFENCE_ENTER",
            lat = 6.5244,
            lon = 3.3792,
            accuracyM = 12.5,
            deviceTs = "2026-07-12T10:00:00Z",
        )

        // Valid JSON object envelope, cross-SDK field names (Flutter parity).
        assertTrue(json.startsWith("{") && json.endsWith("}"))
        assertTrue(json.contains("\"eventId\":\"iqevt_android_abc123\""))
        assertTrue(json.contains("\"locationId\":\"LOC-42\""))
        assertTrue(json.contains("\"eventType\":\"GEOFENCE_ENTER\""))
        assertTrue(json.contains("\"lat\":6.5244"))
        assertTrue(json.contains("\"lon\":3.3792"))
        assertTrue(json.contains("\"accuracyM\":12.5"))
        assertTrue(json.contains("\"deviceTs\":\"2026-07-12T10:00:00Z\""))
        assertTrue(json.contains("\"deviceOs\":\"ANDROID\""))
        assertTrue(json.contains("\"sdkVersion\":"))
    }

    @Test
    fun buildTransitEventJsonOmitsAbsentCoordinates() {
        val json = AddressIQ.buildTransitEventJson(
            eventId = "e1",
            locationCode = "LOC-1",
            eventType = "DWELL",
            lat = null,
            lon = null,
            accuracyM = null,
            deviceTs = "2026-07-12T10:00:00Z",
        )
        assertTrue(!json.contains("\"lat\""))
        assertTrue(!json.contains("\"lon\""))
        assertTrue(!json.contains("\"accuracyM\""))
        assertTrue(json.contains("\"eventType\":\"DWELL\""))
    }

    @Test
    fun eventsBatchEnvelopeIsValidJson() {
        val a = AddressIQ.buildTransitEventJson(
            "e1", "LOC-1", "GEOFENCE_ENTER", 1.0, 2.0, 3.0, "2026-07-12T10:00:00Z",
        )
        val b = AddressIQ.buildTransitEventJson(
            "e2", "LOC-1", "GEOFENCE_EXIT", 1.1, 2.1, 3.1, "2026-07-12T10:05:00Z",
        )
        val body = "{\"events\":[" + listOf(a, b).joinToString(",") + "]}"
        // Re-parse to prove the concatenated envelope is well-formed JSON.
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(body)
        val events = parsed.let { it as kotlinx.serialization.json.JsonObject }["events"]
        assertTrue(events is kotlinx.serialization.json.JsonArray)
        assertEquals(2, (events as kotlinx.serialization.json.JsonArray).size)
    }
}
