package com.addressiq.android

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Does `isEmulator()` actually fire on an emulator?
 *
 * It can only be answered on a device, which is why it was never noticed: the
 * heuristic is the widely-copied Build-property check, and none of its
 * predicates match a current Android Studio AVD. This image reports
 *
 *   FINGERPRINT google/sdk_gphone16k_arm64/emu64a16k:17/…/…:user/dev-keys
 *   MODEL       sdk_gphone16k_arm64
 *   BRAND       google        DEVICE emu64a16k        PRODUCT sdk_gphone16k_arm64
 *
 * — not "generic", not "test-keys" (it is *dev*-keys), no "google_sdk", no
 * "Emulator". So EMULATOR_DETECTED, a *terminating* fraud flag, would never
 * fire for the emulators people actually use.
 */
@RunWith(AndroidJUnit4::class)
class EmulatorDetectionInstrumentedTest {

    @Test
    fun theEmulatorItIsRunningOnIsRecognisedAsOne() {
        assertTrue(
            "isEmulator() returned false on an emulator — " +
                "FINGERPRINT=${Build.FINGERPRINT} MODEL=${Build.MODEL} " +
                "BRAND=${Build.BRAND} DEVICE=${Build.DEVICE} PRODUCT=${Build.PRODUCT}",
            DeviceSignals.isEmulator(),
        )
    }
}
