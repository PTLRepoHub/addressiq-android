package com.addressiq.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.addressiq.android.storage.AddressIQTelemetryQueue
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The device-side geofence trigger, on a real emulator with real Play Services.
 *
 * Everything else about collection has been proven from the server side. This
 * is the one leg that cannot be: whether registering a geofence on an actual
 * device produces an actual transit event. Geofence transitions come from Play
 * Services through a manifest-registered broadcast receiver, so nothing short
 * of a device exercises that path.
 *
 * Deterministic rather than timing-dependent: the controller registers with
 * `INITIAL_TRIGGER_ENTER`, so if the device is already inside the circle when
 * the geofence is added, Play Services raises ENTER immediately. The host puts
 * it there with `adb emu geo fix` before the run — see run-geofence-test.sh.
 */
@RunWith(AndroidJUnit4::class)
class GeofenceTriggerInstrumentedTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Lagos, matching the fixtures used throughout the backend tests. */
    private val lat = 6.5244
    private val lon = 3.3792
    private val radiusM = 150.0

    @Before
    fun drainQueue() {
        // Initialises the encrypted queue as a side effect, which is the only
        // public way in; then start from empty so the assertion below cannot
        // pass on an event left by an earlier run.
        AddressIQ.flushTelemetryBestEffort(context)
        runCatching { AddressIQTelemetryQueue.shared().wipe() }
    }

    @Test
    fun registeringAGeofenceWhileInsideProducesATransitEvent() {
        AddressIQ.initialize(
            AddressIQConfig(
                apiKey = "aiq_test_demo_bank_seed01",
                deployment = AddressIQDeployment.DEVELOPMENT,
            ),
        )

        // The seam a wrapper SDK would use: the verification already exists
        // server-side, so this only lights up OS-level collection.
        AddressIQ.startCollecting(
            context = context,
            locationCode = "loc_geofence_probe",
            verificationCode = "ver_geofence_probe",
            latitude = lat,
            longitude = lon,
            radiusM = radiusM,
        )

        // Play Services delivers the transition through a broadcast; give it a
        // generous window rather than a fixed sleep.
        val deadline = System.currentTimeMillis() + 60_000
        var count = 0
        while (System.currentTimeMillis() < deadline) {
            count = AddressIQTelemetryQueue.shared().count()
            if (count > 0) break
            Thread.sleep(1_000)
        }

        assertTrue(
            "expected a transit event from the geofence ENTER within 60s, queue had $count",
            count > 0,
        )
    }
}
