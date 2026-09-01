package com.addressiq.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.addressiq.android.storage.AddressIQTelemetryQueue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Does a fraud signal survive the whole chain, using the SDK's OWN collector?
 *
 * Every earlier test of this — including the ones written alongside the
 * emulator fix — built `rawPayload` by hand with `isEmulator = true` in the
 * fixture. That proves the engine reacts to a payload; it proves nothing about
 * whether a device ever produces one. It is exactly why `isEmulator()` could
 * return false on a real emulator for so long while every test stayed green,
 * and why EMULATOR_DETECTED never fired in practice.
 *
 * So this test hardcodes nothing. It calls `DeviceSignals.collect(...)`, ships
 * the result through the real queue and the real ingest, and leaves the server
 * to say what it saw. The assertion that matters is the one on the collector's
 * own output: this suite only ever runs on an emulator, so `isEmulator` MUST
 * come back true without anyone having told it so.
 */
@RunWith(AndroidJUnit4::class)
class RealCollectorFraudInstrumentedTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()

    @Test
    fun theCollectorsOwnOutputCarriesTheEmulatorSignalToTheServer() {
        val locationCode = args.getString("aiqLocationCode") ?: ""
        assumeFalse("no verification supplied", locationCode.isEmpty())

        // 1. The collector, unassisted. Nothing here is a fixture.
        val signals = DeviceSignals.collect(context, null)
        assertEquals(
            "DeviceSignals.collect() did not report this emulator as one — " +
                "EMULATOR_DETECTED cannot fire in the field",
            true,
            signals["device"]?.get("isEmulator"),
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

        // 2. The SDK's own envelope builder, fed the collector's own output.
        //    Several events: the engine needs MIN_EVENTS before it scores.
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
            // Prove the signal is on the wire, not merely in memory.
            val json = JSONObject(payload)
            val raw = json.getJSONObject("rawPayload")
            assertEquals(true, raw.getJSONObject("device").getBoolean("isEmulator"))
            queue.enqueue(eventId, payload)
        }
        assertEquals(6, queue.count())

        // 3. The real upload path.
        val flushed = runBlocking { AddressIQ.sync() }
        assertTrue("ingest accepted nothing (flushed=$flushed)", flushed > 0)
        assertEquals("queue did not drain", 0, queue.count())
    }

    /**
     * The spoofing-app detector against a package that is genuinely installed.
     *
     * The runner sideloads a stub carrying one of `SPOOFING_PACKAGES` before
     * this runs. Without that the test skips rather than passing vacuously —
     * asserting `hasSpoofingApps == false` on a clean device would look like
     * coverage while proving only that nothing was found.
     */
    @Test
    fun aRealInstalledSpoofingPackageIsDetected() {
        // `pm list packages` is the shell's view and is not subject to package
        // visibility filtering, so it answers "is it installed" independently
        // of whether the SDK can see it. The two disagreeing is the bug.
        val installedPerShell = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("pm list packages com.lexa.fakegps")
            .let { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).bufferedReader()
                    .use { it.readText() }
            }
            .contains("com.lexa.fakegps")
        assumeFalse(
            "stub spoofing app not installed — install one carrying a package " +
                "id from SPOOFING_PACKAGES before running",
            !installedPerShell,
        )

        // Deliberately NOT an assumption: once the package is installed, the
        // SDK failing to see it is a FAILURE, not a reason to skip. Skipping
        // here is how this went unnoticed — a skipped test is reported inside
        // "OK (n tests)".
        val visibleToSdk = runCatching {
            context.packageManager.getPackageInfo("com.lexa.fakegps", 0)
        }.isSuccess
        assertTrue(
            "com.lexa.fakegps is installed but invisible to the SDK — Android 11+ " +
                "package visibility; the manifest needs a <queries> entry",
            visibleToSdk,
        )

        val signals = DeviceSignals.collect(context, null)
        val security = signals["security"]!!
        assertEquals(
            "a known fake-GPS package is installed and was not detected",
            true,
            security["hasSpoofingApps"],
        )
        @Suppress("UNCHECKED_CAST")
        val found = security["spoofingAppsFound"] as List<String>
        assertTrue("com.lexa.fakegps missing from $found", found.contains("com.lexa.fakegps"))
    }

    /**
     * Root detection cannot be exercised on a stock Play-image emulator — the
     * markers are files only a rooted device has, and creating them needs the
     * root we are trying to detect. What IS verifiable is the absence of a
     * false positive, which is the failure mode that would silently disqualify
     * honest users.
     */
    @Test
    fun rootDetectionDoesNotFireOnACleanDevice() {
        val signals = DeviceSignals.collect(context, null)
        assertEquals(
            "isRooted fired on a stock emulator — every honest device would be flagged",
            false,
            signals["security"]?.get("isRooted"),
        )
    }
}
