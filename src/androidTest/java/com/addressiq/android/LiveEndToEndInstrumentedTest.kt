package com.addressiq.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.addressiq.android.storage.AddressIQTelemetryQueue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole chain from an emulator: register a real geofence, let Play Services
 * raise the transition, and upload through the real ingest.
 *
 * Pointed at a verification created beforehand through the public API; the
 * codes arrive as instrumentation arguments (see run-live-e2e.sh) rather than
 * being baked in, so the test does not pass once and rot.
 *
 * The counterpart of LiveEndToEndTests.swift in the iOS SDK. Between them they
 * cover the only leg no server-side test can reach: that an app physically
 * inside a boundary produces an event that ingest accepts.
 */
@RunWith(AndroidJUnit4::class)
class LiveEndToEndInstrumentedTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()

    private val locationCode get() = args.getString("aiqLocationCode") ?: ""
    private val verificationCode get() = args.getString("aiqVerificationCode") ?: ""

    /**
     * Which backend to point at — DEVELOPMENT (local stack) or STAGING. The
     * same test covers both: staging is HTTPS against a separate ingest host,
     * development is cleartext to the emulator's host alias, and those are
     * exactly the two transport paths worth proving.
     */
    private val deployment: AddressIQDeployment
        get() = when (args.getString("aiqDeployment")?.uppercase()) {
            "STAGING" -> AddressIQDeployment.STAGING
            else -> AddressIQDeployment.DEVELOPMENT
        }

    private val apiKey get() = args.getString("aiqApiKey") ?: "aiq_test_demo_bank_seed01"

    private val lat = 6.5244
    private val lon = 3.3792

    @Before
    fun drainQueue() {
        AddressIQ.flushTelemetryBestEffort(context)
        runCatching { AddressIQTelemetryQueue.shared().wipe() }
    }

    @Test
    fun collectUploadsThroughRealIngestAndDrainsTheQueue() {
        assumeFalse(
            "no verification supplied — run via scripts/run-live-e2e.sh",
            locationCode.isEmpty() || verificationCode.isEmpty(),
        )

        AddressIQ.initialize(
            AddressIQConfig(apiKey = apiKey, deployment = deployment),
        )
        AddressIQ.startCollecting(
            context = context,
            locationCode = locationCode,
            verificationCode = verificationCode,
            latitude = lat,
            longitude = lon,
            radiusM = 150.0,
        )

        val queue = AddressIQTelemetryQueue.shared()
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline && queue.count() == 0) {
            Thread.sleep(1_000)
        }
        assertTrue("geofence never produced an event", queue.count() > 0)

        // What is about to be uploaded must be something ingest accepts —
        // `verificationId` on the envelope would 400 the entire batch.
        val peeked = queue.dequeue(1).firstOrNull()
        assertNotNull("queue reported events but dequeued none", peeked)
        val json = JSONObject(peeked!!.payload)
        assertEquals(locationCode, json.optString("locationId"))
        assertEquals("ANDROID", json.optString("deviceOs"))
        assertTrue("envelope carries verificationId", !json.has("verificationId"))
        assertTrue("no device signals — every fraud check is dark", json.has("rawPayload"))

        // Upload for real. `sync` returns how many rows left the queue, which is
        // non-zero only if the server accepted AND the ack was recognised.
        val flushed = runBlocking { AddressIQ.sync() }
        assertTrue("ingest accepted nothing, or the ack was not recognised", flushed > 0)
    }

    /**
     * The upload leg on its own, independent of whether the emulator's GPS
     * cooperates.
     *
     * The geofence trigger is covered by [GeofenceTriggerInstrumentedTest]; what
     * this pins is everything after it — the SDK's own envelope, the encrypted
     * queue, the resolved ingest host, and whether the acknowledgement is
     * recognised so the queue actually drains. All of that is invisible to a
     * unit test and was where the real bugs lived.
     */
    @Test
    fun theSdkUploadsItsOwnEnvelopeToRealIngestAndDrainsTheQueue() {
        assumeFalse(
            "no verification supplied — run via scripts/run-live-e2e.sh",
            locationCode.isEmpty(),
        )

        AddressIQ.initialize(
            AddressIQConfig(apiKey = apiKey, deployment = deployment),
        )
        AddressIQ.flushTelemetryBestEffort(context)
        val queue = AddressIQTelemetryQueue.shared()
        queue.wipe()

        val eventId = java.util.UUID.randomUUID().toString()
        val payload = AddressIQ.buildTransitEventJson(
            eventId = eventId,
            locationCode = locationCode,
            eventType = "GEOFENCE_ENTER",
            lat = lat,
            lon = lon,
            accuracyM = 12.0,
            deviceSignals = mapOf(
                "device" to mapOf("isEmulator" to true),
                "security" to mapOf("isRooted" to false),
            ),
        )
        queue.enqueue(eventId, payload)
        assertEquals("event did not persist to the encrypted queue", 1, queue.count())

        val flushed = runBlocking { AddressIQ.sync() }
        assertTrue(
            "ingest accepted nothing, or the ack was not recognised (queue=${queue.count()})",
            flushed > 0,
        )
        assertEquals("queue did not drain", 0, queue.count())
    }
}
