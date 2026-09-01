package com.addressiq.android

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.addressiq.android.storage.AddressIQTelemetryQueue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MOCK_LOCATION, driven by an actual mock location provider.
 *
 * This is the attack the product most needs to catch — a fake-GPS app reporting
 * a home address the person has never been to — and the flag is terminal, so
 * getting it wrong either lets fraud through or disqualifies honest users.
 *
 * It is exercised here through `LocationManager.addTestProvider`, which is how
 * a fake-GPS app actually works: the platform marks locations from a test
 * provider, and the SDK reads that mark. A `Location` with the flag set by hand
 * would test the SDK's getter and not the platform contract behind it.
 *
 * Needs the mock-location app-op, which cannot be granted from inside the
 * process:
 *
 *   adb shell appops set com.addressiq.android.test android:mock_location allow
 *
 * Without it the test skips rather than passing vacuously.
 */
@RunWith(AndroidJUnit4::class)
class MockLocationInstrumentedTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val lm get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val provider = "aiq-mock-probe"
    private var providerAdded = false

    @Before
    fun addTestProvider() {
        providerAdded = runCatching {
            lm.addTestProvider(
                provider,
                false, false, false, false,
                true, true, true,
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE,
            )
            lm.setTestProviderEnabled(provider, true)
        }.isSuccess
    }

    @After
    fun removeTestProvider() {
        if (providerAdded) runCatching { lm.removeTestProvider(provider) }
    }

    /** A location delivered through the test provider, as a fake-GPS app would. */
    private fun mockedFix(): Location {
        val fix = Location(provider).apply {
            latitude = 6.5244
            longitude = 3.3792
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        lm.setTestProviderLocation(provider, fix)
        return lm.getLastKnownLocation(provider) ?: fix
    }

    @Test
    fun aFixFromATestProviderIsRecognisedAsMocked() {
        assumeFalse(
            "mock-location app-op not granted — run: adb shell appops set " +
                "com.addressiq.android.test android:mock_location allow",
            !providerAdded,
        )
        val fix = mockedFix()
        // The platform's own mark, not one this test set.
        assertTrue("the platform did not mark the test-provider fix as mock", fix.isMock)
        assertTrue("isMocked() did not recognise a mocked fix", DeviceSignals.isMocked(fix))
    }

    @Test
    fun anOrdinaryFixIsNotReportedAsMocked() {
        // The false-positive case: disqualifying honest users is the worse
        // failure of the two, since MOCK_LOCATION is terminal.
        val plain = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 6.5244
            longitude = 3.3792
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        assertFalse("an unmocked fix was reported as mocked", DeviceSignals.isMocked(plain))
        assertFalse("a null fix was reported as mocked", DeviceSignals.isMocked(null))
    }

    @Test
    fun theCollectorCarriesItAndTheEngineRaisesMockLocation() {
        assumeFalse("mock-location app-op not granted", !providerAdded)
        val locationCode = args.getString("aiqLocationCode") ?: ""
        assumeFalse("no verification supplied", locationCode.isEmpty())

        val signals = DeviceSignals.collect(context, mockedFix())
        assertEquals(
            "collect() did not carry the mock-location signal",
            true,
            signals["location"]?.get("isMocked"),
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
                accuracyM = 5.0,
                deviceSignals = signals,
            )
            assertTrue(
                "isMocked missing from the wire envelope",
                JSONObject(payload)
                    .getJSONObject("rawPayload")
                    .getJSONObject("location")
                    .getBoolean("isMocked"),
            )
            queue.enqueue(eventId, payload)
        }

        val flushed = runBlocking { AddressIQ.sync() }
        assertTrue("ingest accepted nothing (flushed=$flushed)", flushed > 0)
        assertEquals("queue did not drain", 0, queue.count())
    }
}
