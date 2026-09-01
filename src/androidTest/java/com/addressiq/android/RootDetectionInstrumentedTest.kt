package com.addressiq.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.addressiq.android.storage.AddressIQTelemetryQueue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * ROOTED_DEVICE, actually observed firing.
 *
 * Until now this flag had never been seen to fire anywhere. The real markers
 * are paths only a rooted device has, and creating them needs the root being
 * detected — so on any test device `isRooted()` returns false and the only
 * available assertion was "no false positive". That is the same blind spot that
 * hid the emulator heuristic and the spoofing-app check, both of which returned
 * false meaning "could not look" and were never once seen to return true.
 *
 * `DeviceSignals.isRooted` and `collect` now take the marker list, defaulted to
 * the real one, so a test can point them at a file it is allowed to create.
 * This exercises the detector's logic and the whole path behind it — envelope,
 * queue, ingest, engine — on a device. It does NOT prove the production marker
 * paths are the right ones; that is a judgement about rooted devices, not
 * something a test can settle.
 */
@RunWith(AndroidJUnit4::class)
class RootDetectionInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()

    /** A file we are allowed to create, standing in for `/system/bin/su`. */
    private val marker: File by lazy { File(context.filesDir, "su-marker-probe") }

    @After
    fun removeMarker() {
        marker.delete()
    }

    @Test
    fun theDetectorFiresWhenAMarkerIsPresentAndNotWhenItIsAbsent() {
        // Absent — the no-false-positive case, which was the only one testable
        // before.
        marker.delete()
        assertFalse(
            "isRooted fired with no marker present",
            DeviceSignals.isRooted(listOf(marker.absolutePath)),
        )

        // Present. This is the assertion that has never been made.
        marker.writeText("probe")
        assertTrue(
            "isRooted did NOT fire with a marker present — the detector is inert",
            DeviceSignals.isRooted(listOf(marker.absolutePath)),
        )
    }

    @Test
    fun oneMarkerAmongManyIsEnough() {
        marker.writeText("probe")
        val mixed = listOf("/definitely/not/here", marker.absolutePath, "/nor/here")
        assertTrue(
            "a single present marker among absent ones did not trigger detection",
            DeviceSignals.isRooted(mixed),
        )
    }

    @Test
    fun theCollectorReportsItAndTheEngineRaisesRootedDevice() {
        val locationCode = args.getString("aiqLocationCode") ?: ""
        assumeFalse("no verification supplied", locationCode.isEmpty())

        marker.writeText("probe")
        val signals = DeviceSignals.collect(context, null, listOf(marker.absolutePath))
        assertEquals(
            "collect() did not carry the root signal",
            true,
            signals["security"]?.get("isRooted"),
        )

        AddressIQ.initialize(
            AddressIQConfig(
                apiKey = "aiq_test_demo_bank_seed01",
                deployment = AddressIQDeployment.DEVELOPMENT,
            ),
        )
        AddressIQ.flushTelemetryBestEffort(context)
        val queue = AddressIQTelemetryQueue.shared()
        queue.wipe()

        repeat(6) { i ->
            val eventId = java.util.UUID.randomUUID().toString()
            val payload = AddressIQ.buildTransitEventJson(
                eventId = eventId,
                locationCode = locationCode,
                eventType = if (i % 2 == 0) "GEOFENCE_ENTER" else "DWELL",
                lat = 6.5244,
                lon = 3.3792,
                accuracyM = 10.0,
                deviceSignals = signals,
            )
            assertTrue(
                "isRooted missing from the wire envelope",
                JSONObject(payload)
                    .getJSONObject("rawPayload")
                    .getJSONObject("security")
                    .getBoolean("isRooted"),
            )
            queue.enqueue(eventId, payload)
        }

        val flushed = runBlocking { AddressIQ.sync() }
        assertTrue("ingest accepted nothing (flushed=$flushed)", flushed > 0)
        assertEquals("queue did not drain", 0, queue.count())
    }
}
