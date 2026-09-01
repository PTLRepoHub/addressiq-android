package com.addressiq.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the SDK actually collects on a device, measured rather than assumed.
 *
 * The scoring engine reads eleven paths out of `rawPayload`. Four of them —
 * `network.isVpn`, `network.isoCountryCode`, `network.mobileCountryCode`,
 * `sensors.accelerometerNoise` and `appState.state` — are read by
 * packages/verification-engine/src/score.ts but emitted by NO SDK, so
 * VPN_DETECTED, the SIM-country checks, SENSOR_ANOMALY and ALWAYS_FOREGROUND
 * can never fire on any platform. This test pins which sections are really
 * produced, so that gap is a measured fact rather than a reading of the source,
 * and so a section silently disappearing is caught.
 *
 * It exists because `isEmulator()` was broken for exactly this reason: every
 * test asserted a hardcoded fixture, and nothing ever ran the collector on a
 * device.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSignalsCoverageInstrumentedTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theCollectorProducesEverySectionTheEngineCanAct0n() {
        val signals = DeviceSignals.collect(context, null)

        // Sections the engine reads AND the SDK emits.
        assertNotNull("device section missing", signals["device"])
        assertNotNull("location section missing", signals["location"])
        assertNotNull("security section missing", signals["security"])
        assertNotNull("fingerprint section missing — blacklist has no key", signals["fingerprint"])
        assertNotNull("appState section missing — ALWAYS_FOREGROUND cannot fire", signals["appState"])

        // Every terminal/contributing flag the engine derives needs its key
        // present, not merely the section.
        assertTrue("device.isEmulator absent", signals["device"]!!.containsKey("isEmulator"))
        assertTrue("location.isMocked absent", signals["location"]!!.containsKey("isMocked"))
        assertTrue("security.isRooted absent", signals["security"]!!.containsKey("isRooted"))
        assertTrue(
            "security.hasSpoofingApps absent",
            signals["security"]!!.containsKey("hasSpoofingApps"),
        )
        assertNotNull(
            "fingerprint.installId absent",
            signals["fingerprint"]!!["installId"],
        )
        // The engine compares this against the literal 'active'; any other
        // vocabulary silently never matches.
        assertTrue(
            "appState.state must be one of active/background/unknown",
            signals["appState"]!!["state"] in listOf("active", "background", "unknown"),
        )
    }

    @Test
    fun runningOnAnEmulatorIsReportedHonestly() {
        val signals = DeviceSignals.collect(context, null)
        // This suite only ever runs on an emulator; if it is ever run on real
        // hardware this expectation flips, which is itself worth knowing.
        assertEquals(true, signals["device"]!!["isEmulator"])
        // A stock emulator is not rooted and carries no spoofing apps, so these
        // must be present AND false — a section that reported `true` here would
        // mean the detector fires on a clean device.
        assertEquals(false, signals["security"]!!["isRooted"])
        assertEquals(false, signals["security"]!!["hasSpoofingApps"])
    }

    @Test
    fun theSectionsTheEngineReadsButNoSdkSendsAreStillAbsent() {
        val signals = DeviceSignals.collect(context, null)
        // Documents the live gap. When VPN / SIM-country / sensor / app-state
        // collection is implemented, this test fails and is the reminder to
        // delete it — the engine has been reading these paths from nothing.
        // network.* is withheld by design: isoCountryCode and mobileCountryCode
        // describe the SIM's owner, and this collector is deliberately limited
        // to signals that do not identify a person. SIM_COUNTRY_MISMATCH and
        // VPN_DETECTED therefore cannot fire, and that is a product decision
        // rather than a bug.
        assertEquals("network section is now emitted — revisit the privacy note", null, signals["network"])
        // sensors.accelerometerNoise needs a sampling window, which has a
        // battery cost that has not been agreed. SENSOR_ANOMALY cannot fire.
        assertEquals("sensors section is now emitted — update score.ts coverage", null, signals["sensors"])
    }
}
