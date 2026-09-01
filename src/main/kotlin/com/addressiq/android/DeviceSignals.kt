package com.addressiq.android

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import com.addressiq.android.storage.TinkSecureKeyValueStore
import java.io.File
import java.util.UUID

/**
 * Device-integrity signals attached to every transit event.
 *
 * Deliberately minimal. They answer "is this device lying about where it is"
 * without describing who owns it: no IP, no carrier, no SIM country, no WiFi.
 * Those are personal data and remain a separate decision — which is why
 * `network.isoCountryCode` and `network.mobileCountryCode`, both read by the
 * scoring engine, are not collected here.
 *
 * The one identifier is `fingerprint.installId`, added deliberately and handled
 * as personal data: it is per-install, dies with the install, and exists to key
 * DEVICE_CHANGE and the device blacklist.
 */
internal object DeviceSignals {

    private const val INSTALL_ID_KEY = "addressiq_install_id"

    /** Package names that can feed a fake fix to any app on the device. */
    private val SPOOFING_PACKAGES = listOf(
        "com.lexa.fakegps",
        "com.incorporateapps.fakegps.fre",
        "com.blogspot.newapphorizons.fakegps",
        "com.theappninjas.fakegpsjoystick",
        "com.rosteam.gpsemulator",
    )

    /** Paths that only exist once a device has been rooted. */
    private val ROOT_MARKERS = listOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
    )

    /**
     * Build the `rawPayload` sections for one event. `location` supplies the
     * per-fix mock flag; everything else describes the device.
     */
    fun collect(context: Context, location: Location?): Map<String, Map<String, Any>> {
        val spoofingApps = installedSpoofingApps(context)
        val signals = mutableMapOf(
            "device" to mapOf<String, Any>("isEmulator" to isEmulator()),
            "location" to mapOf<String, Any>("isMocked" to isMocked(location)),
            // Whether the app was in the foreground when this event was
            // recorded. The engine raises ALWAYS_FOREGROUND when nearly every
            // "background" ping says `active`, which is what driving the app by
            // hand looks like. Nothing was ever sent on this path, so that
            // check could not fire.
            "appState" to mapOf<String, Any>("state" to appState(context)),
            "security" to mapOf<String, Any>(
                "isRooted" to isRooted(),
                "hasSpoofingApps" to spoofingApps.isNotEmpty(),
                "spoofingAppsFound" to spoofingApps,
            ),
        )
        installId(context)?.let { signals["fingerprint"] = mapOf("installId" to it) }
        return signals
    }

    /**
     * Random per-install identifier, minted once and kept in the encrypted
     * store.
     *
     * Deliberately not a hardware id: it identifies this installation only, is
     * scoped to this app, and dies when the app's data is cleared or the app is
     * uninstalled. It is what links the same install verifying several
     * different addresses.
     */
    fun installId(context: Context): String? = runCatching {
        val store = TinkSecureKeyValueStore(context.applicationContext)
        store.get(INSTALL_ID_KEY) ?: UUID.randomUUID().toString().also {
            store.put(INSTALL_ID_KEY, it)
        }
    }.getOrNull()

    /**
     * Emulators are identified by their build fingerprint. This catches the
     * common images (Android Studio, Genymotion); a purpose-built image can
     * defeat it, which is why it is one signal among several.
     */
    /**
     * `active` when this process is in the foreground, `background` otherwise.
     *
     * Uses the importance of our own process rather than any lifecycle
     * observer, so it is correct when called from a broadcast receiver or a
     * WorkManager job — which is where transit events are actually built, and
     * where no Activity exists to ask.
     */
    private fun appState(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return "unknown"
        val mine = runCatching { am.runningAppProcesses }.getOrNull()
            ?.firstOrNull { it.pid == android.os.Process.myPid() }
            ?: return "unknown"
        return if (
            mine.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        ) "active" else "background"
    }

    fun isEmulator(): Boolean {
        // `ro.hardware` and the qemu flags first: they are what actually holds
        // on a current AVD. The Build-property checks below them are the
        // widely-copied heuristic, and on their own they DO NOT FIRE on a
        // modern image — verified on a real API 37 emulator reporting
        // `google/sdk_gphone16k_arm64/emu64a16k:…:user/dev-keys`, which is not
        // "generic", not "test-keys" (it is dev-keys), and contains neither
        // "google_sdk" nor "Emulator". EMULATOR_DETECTED is a *terminating*
        // flag, so for as long as that was the whole check it never fired for
        // the emulators people actually use. See
        // EmulatorDetectionInstrumentedTest, which fails without this.
        val hardware = systemProperty("ro.hardware")
        if (hardware == "ranchu" || hardware == "goldfish" || hardware == "vbox86") return true
        if (systemProperty("ro.kernel.qemu").isNotEmpty()) return true
        if (systemProperty("ro.boot.qemu") == "1") return true

        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.FINGERPRINT.contains("test-keys") ||
            Build.MODEL.startsWith("sdk_") ||
            Build.PRODUCT.startsWith("sdk_") ||
            Build.DEVICE.startsWith("emu") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.PRODUCT == "google_sdk"
    }

    /**
     * Read a system property reflectively.
     *
     * `android.os.SystemProperties` is hidden API, so reflection is the
     * supported route to `ro.hardware` / `ro.kernel.qemu` — the two properties
     * that actually identify a modern emulator. Returns "" on any failure,
     * which the caller reads as "not observed" rather than "not an emulator".
     */
    private fun systemProperty(key: String): String = runCatching {
        @Suppress("PrivateApi")
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String).orEmpty()
    }.getOrDefault("")

    /**
     * Whether this fix came from a mock provider. `isFromMockProvider` was
     * replaced by `isMock` in API 31; the old one still answers on older
     * devices, which is most of the fleet.
     */
    @Suppress("DEPRECATION")
    fun isMocked(location: Location?): Boolean {
        if (location == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            location.isFromMockProvider
        }
    }

    fun isRooted(): Boolean = ROOT_MARKERS.any { runCatching { File(it).exists() }.getOrDefault(false) }

    private fun installedSpoofingApps(context: Context): List<String> =
        SPOOFING_PACKAGES.filter { packageName ->
            runCatching {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            }.getOrDefault(false)
        }
}
