package com.addressiq.android.permissions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * SDK-owned permission orchestrator.
 *
 * **Permission Trigger Ownership principle:** the integrating app
 * decides *when* verification begins. Everything after — checking
 * grants, driving the OS prompt, recovering denial, deep-linking to
 * Settings — is the SDK's job. This object is where that ownership
 * actually lives.
 *
 * Two prompt paths:
 *
 *   1. **`ComponentActivity` (modern AndroidX)** — uses
 *      `ActivityResultRegistry.register(...)` so we can launch the
 *      prompt from anywhere in the activity lifecycle without a
 *      pre-onCreate registration step. This is the path every modern
 *      app (AppCompatActivity / FragmentActivity / Compose activity)
 *      will take.
 *   2. **Plain `Activity` fallback** — uses
 *      `ActivityCompat.requestPermissions(...)` and parks a callback
 *      in [pendingLegacy] keyed by request code. Partners on
 *      non-AndroidX hosts forward their activity's
 *      `onRequestPermissionsResult` to
 *      [AddressIQ.handlePermissionResult] which routes back here.
 *
 * Three-stage sequencing (per Google policy + best practice):
 *
 *   Stage 1: `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`
 *   Stage 2: `POST_NOTIFICATIONS` (Android 13+ only)
 *   Stage 3: `ACCESS_BACKGROUND_LOCATION` (Android 10+ only, and only
 *            if Stage 1 granted — Android 11+ ignores combined
 *            foreground+background requests and force-redirects to
 *            Settings; we sequence them so the prompt actually appears.)
 */
internal object PermissionRequester {

    private const val GRANTED = "GRANTED"
    private const val DENIED = "DENIED"
    private const val NOT_DETERMINED = "NOT_DETERMINED"
    private const val BLOCKED = "BLOCKED"
    private const val UNAVAILABLE = "UNAVAILABLE"

    private val pendingLegacy = ConcurrentHashMap<Int, (Map<String, Boolean>) -> Unit>()
    private val legacyRequestCodeSeq = AtomicInteger(0x41_49_00) // "AI" prefix, 24-bit

    // ── Public reads ────────────────────────────────────────────────────

    fun getPermissionState(context: Context): Map<String, String> {
        val fg = stateForCheck(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            stateForCheck(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            fg
        }
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stateForCheck(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GRANTED
        }
        return mapOf(
            "foregroundLocation" to fg,
            "backgroundLocation" to bg,
            "preciseLocation" to accuracyState(context),
            "notifications" to notifications,
        )
    }

    /**
     * Precise-vs-approximate accuracy. Cross-SDK value set:
     * `{GRANTED, REDUCED, NOT_DETERMINED}`. On Android 12+ the user can grant
     * approximate (COARSE) while denying precise (FINE) — that surfaces as
     * REDUCED so the flow can re-prompt for precise. FINE granted → GRANTED.
     */
    fun accuracyState(context: Context): String = when {
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) -> GRANTED
        isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) -> "REDUCED"
        else -> NOT_DETERMINED
    }

    fun shouldShowRationale(activity: Activity): Map<String, Boolean> {
        val perms = mutableMapOf<String, Boolean>(
            "foregroundLocation" to ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms["backgroundLocation"] = ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms["notifications"] = ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        return perms
    }

    fun openSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    // ── Public request ──────────────────────────────────────────────────

    /**
     * Drive the OS prompt sequence. Returns the final permission state
     * after all stages complete.
     *
     * Stages that would no-op (already granted, or below the relevant
     * API level) are skipped. If foreground is denied, background is
     * not requested (Android wouldn't show the prompt anyway).
     */
    suspend fun requestPermissions(activity: Activity): Map<String, String> {
        // Stage 1: foreground location.
        val foregroundPerms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (foregroundPerms.any { !isGranted(activity, it) }) {
            requestRaw(activity, foregroundPerms)
        }

        // Stage 2: notifications (Android 13+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            requestRaw(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }

        // Stage 3: background location (Android 10+). Only if foreground
        // is granted — Android 11+ silently no-ops background requests
        // when foreground is missing.
        val fineGrantedNow = isGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            fineGrantedNow &&
            !isGranted(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            requestRaw(activity, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }

        // Final state — but use the rationale-aware computation so
        // permanently-denied perms surface as BLOCKED rather than DENIED.
        return computePostRequestState(activity)
    }

    /**
     * Drive the OS toward the combination address verification needs: precise
     * (FINE) + background/Always. Runs the standard sequence, then — mirroring
     * the widget's "keep re-prompting until Precise is on" screen — re-requests
     * FINE if the user only granted approximate. Returns the final state.
     */
    suspend fun requestPreciseAndAlways(activity: Activity): Map<String, String> {
        requestPermissions(activity)
        // If the user granted approximate only, re-prompt for precise (Android
        // 12+ shows an upgrade prompt for FINE alongside a held COARSE grant).
        if (accuracyState(activity) == "REDUCED") {
            requestRaw(
                activity,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
        return computePostRequestState(activity)
    }

    /**
     * Bridge from a legacy `Activity.onRequestPermissionsResult(...)`
     * override into the suspended request. Partners on non-AndroidX
     * activities forward all permission results here; if the request
     * code matches one we issued, the suspended caller resumes.
     * Returns true when the call was handled (so partners can
     * short-circuit their own handling).
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ): Boolean {
        val handler = pendingLegacy.remove(requestCode) ?: return false
        val results = permissions.indices.associate { idx ->
            permissions[idx] to (grantResults[idx] == PackageManager.PERMISSION_GRANTED)
        }
        handler(results)
        return true
    }

    // ── Internals ───────────────────────────────────────────────────────

    private suspend fun requestRaw(
        activity: Activity,
        perms: Array<String>,
    ): Map<String, Boolean> {
        return if (activity is ComponentActivity) {
            requestViaActivityResult(activity, perms)
        } else {
            requestViaLegacy(activity, perms)
        }
    }

    private suspend fun requestViaActivityResult(
        activity: ComponentActivity,
        perms: Array<String>,
    ): Map<String, Boolean> = suspendCancellableCoroutine { cont ->
        val key = "addressiq.permissions.${UUID.randomUUID()}"
        var launcher: ActivityResultLauncher<Array<String>>? = null
        launcher = activity.activityResultRegistry.register(
            key,
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results: Map<String, Boolean> ->
            launcher?.unregister()
            if (cont.isActive) cont.resume(results)
        }
        cont.invokeOnCancellation { launcher.unregister() }
        launcher.launch(perms)
    }

    private suspend fun requestViaLegacy(
        activity: Activity,
        perms: Array<String>,
    ): Map<String, Boolean> = suspendCancellableCoroutine { cont ->
        val requestCode = legacyRequestCodeSeq.incrementAndGet() and 0xFFFF
        pendingLegacy[requestCode] = { results: Map<String, Boolean> ->
            if (cont.isActive) cont.resume(results)
        }
        cont.invokeOnCancellation { pendingLegacy.remove(requestCode) }
        ActivityCompat.requestPermissions(activity, perms, requestCode)
    }

    private fun stateForCheck(context: Context, permission: String): String {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return GRANTED
        // Without an Activity we can't tell BLOCKED from NOT_DETERMINED.
        // Caller wanting the more precise tri-state should call
        // requestPermissions(activity) which uses computePostRequestState.
        if (context is Activity) {
            return if (ActivityCompat.shouldShowRequestPermissionRationale(context, permission)) {
                DENIED
            } else {
                NOT_DETERMINED
            }
        }
        return NOT_DETERMINED
    }

    private fun computePostRequestState(activity: Activity): Map<String, String> {
        fun post(permission: String): String {
            if (isGranted(activity, permission)) return GRANTED
            // After the prompt: rationale=true → user denied this round (can re-ask);
            // rationale=false → first ask hadn't happened (impossible post-prompt)
            // OR user picked "Don't ask again" → BLOCKED.
            return if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                DENIED
            } else {
                BLOCKED
            }
        }

        val fg = post(Manifest.permission.ACCESS_FINE_LOCATION)
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            post(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            fg
        }
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            post(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GRANTED
        }
        return mapOf(
            "foregroundLocation" to fg,
            "backgroundLocation" to bg,
            "notifications" to notifications,
        )
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
