package com.addressiq.android.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import com.addressiq.android.AddressIQ
import com.addressiq.android.AddressIQDeployment
import com.addressiq.android.generated.AddressIQBuildConfig
import com.addressiq.android.ui.AddressIQVerifyInput
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Hosts the shared AddressIQ web widget (the single cross-platform source of
 * truth for the collect/verify UI) in a [WebView]. This native shell owns only
 * the parts a webview cannot: the precise/Always location prompt and the fix.
 *
 * Bridge (matches the web `HostBridge`):
 *   JS → native via `window.AddressIQAndroid.postMessage(JSON)`:
 *     { kind: 'event',   name, payload }
 *     { kind: 'request', id, action, payload }
 *   native → JS: `window.AddressIQBridge.resolve(id, result)` / `.reject(id, err)`
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AddressIQWebFlow(
    input: AddressIQVerifyInput,
    apiUrl: String,
    /** Development-only override. `null` means "resolve normally": the SRI-pinned CDN build (the only source). */
    widgetUrl: String?,
    onCompleted: (locationCode: String, formattedAddress: String?) -> Unit,
    onCancelled: () -> Unit,
    onFailed: (code: String, message: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                val bridge = WebFlowBridge(this, activity, scope, onCompleted, onCancelled, onFailed)
                addJavascriptInterface(bridge, "AddressIQAndroid")
                // The widget is loaded from the SRI-pinned CDN (the only source — the
                // SDK ships no bundled copy). A failed load reports WIDGET_LOAD_FAILED
                // through the bridge above.
                loadDataWithBaseURL(apiUrl, flowHtml(input, apiUrl, widgetUrl), "text/html", "utf-8", null)
            }
        },
    )
}

/** JS-facing bridge. `@JavascriptInterface` methods run off the main thread. */
private class WebFlowBridge(
    private val webView: WebView,
    private val activity: Activity?,
    private val scope: CoroutineScope,
    private val onCompleted: (String, String?) -> Unit,
    private val onCancelled: () -> Unit,
    private val onFailed: (String, String) -> Unit,
) {
    private val router = WebFlowMessageRouter(
        // Callbacks touch the Activity result — hop to the main thread.
        onCompleted = { code, addr -> webView.post { onCompleted(code, addr) } },
        onCancelled = { webView.post { onCancelled() } },
        onFailed = { code, message -> webView.post { onFailed(code, message) } },
        onLocationRequest = { id ->
            val act = activity
            if (act == null) reject(id, "NO_ACTIVITY", "No hosting activity for permission prompt")
            else scope.launch { provideLocation(id, act) }
        },
        onPermissionRequest = { id ->
            val act = activity
            if (act == null) reject(id, "NO_ACTIVITY", "No hosting activity for permission prompt")
            else scope.launch { requestPermission(id, act) }
        },
        onGetPermissionState = { id ->
            // Read WITHOUT prompting — the Settings screen polls this to detect
            // Always + Precise after the user returns from the OS Settings app.
            val state = AddressIQ.getPermissionState(webView.context)
            val json = JSONObject()
                .put("foreground", state["foregroundLocation"] == "GRANTED")
                .put("background", state["backgroundLocation"] == "GRANTED")
            resolveRaw(id, json.toString())
        },
        onOpenSettings = { id ->
            openAppSettings(webView.context)
            resolveRaw(id, "true")
        },
        permissionStatus = {
            val fg = AddressIQ.getPermissionState(webView.context)["foregroundLocation"] ?: "NOT_DETERMINED"
            WebFlowMessageRouter.webPermission(fg)
        },
        resolveString = { id, value -> resolve(id, value) },
        reject = { id, code, message -> reject(id, code, message) },
    )

    @JavascriptInterface
    fun postMessage(message: String) = router.handle(message)

    /**
     * Run the Always + Precise permission prompt (the moment the OS dialog appears,
     * on the "Verify where you currently live" screen). Resolves with whether
     * foreground was granted; the web flow proceeds either way.
     */
    private suspend fun requestPermission(id: String, act: Activity) {
        val state = AddressIQ.requestPreciseAndAlways(act)
        // `foregroundLocation` tracks ACCESS_FINE_LOCATION, so GRANTED already
        // means precise — approximate-only (COARSE) reports false, gating the flow.
        val json = JSONObject()
            .put("foreground", state["foregroundLocation"] == "GRANTED")
            .put("background", state["backgroundLocation"] == "GRANTED")
        resolveRaw(id, json.toString())
    }

    /** Ensure precise + Always, then return a one-shot fix. The native prompt appears here. */
    private suspend fun provideLocation(id: String, act: Activity) {
        val state = AddressIQ.requestPreciseAndAlways(act)
        if (state["foregroundLocation"] != "GRANTED") {
            reject(id, "PERMISSION_DENIED", "Location permission not granted")
            return
        }
        try {
            val client = LocationServices.getFusedLocationProviderClient(act)
            val loc = withContext(Dispatchers.Main) {
                // A fresh high-accuracy fix can be null on emulators / cold start;
                // fall back to the last known location so the fix still resolves.
                com.google.android.gms.tasks.Tasks.await(
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null),
                ) ?: com.google.android.gms.tasks.Tasks.await(client.lastLocation)
            }
            if (loc == null) {
                reject(id, "LOCATION_UNAVAILABLE", "No location fix available")
            } else {
                val json = JSONObject()
                    .put("lat", loc.latitude)
                    .put("lon", loc.longitude)
                    .put("accuracy", loc.accuracy.toDouble())
                resolveRaw(id, json.toString())
            }
        } catch (e: SecurityException) {
            reject(id, "PERMISSION_DENIED", e.message ?: "Location permission revoked")
        } catch (e: Exception) {
            reject(id, "LOCATION_UNAVAILABLE", e.message ?: "Failed to get location")
        }
    }

    // ── native → JS ──

    private fun resolve(id: String, stringResult: String) =
        resolveRaw(id, JSONObject.quote(stringResult))

    private fun resolveRaw(id: String, jsonResult: String) =
        evaluate("window.AddressIQBridge && window.AddressIQBridge.resolve(${JSONObject.quote(id)}, $jsonResult);")

    private fun reject(id: String, code: String, message: String) {
        val err = JSONObject().put("code", code).put("message", message)
        evaluate("window.AddressIQBridge && window.AddressIQBridge.reject(${JSONObject.quote(id)}, ${err});")
    }

    private fun evaluate(js: String) {
        webView.post { webView.evaluateJavascript(js, null) }
    }

    /** Open this app's OS settings page (App info → Permissions → Location). */
    private fun openAppSettings(context: android.content.Context) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", context.packageName, null),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

/**
 * Inline page mounting the widget. `locationProvider` is omitted so the widget
 * auto-selects its `BridgeLocationProvider` (native owns precise/Always).
 */
/** Compose [Color] → `#RRGGBB`, the format the widget's CSS branding vars expect. */
private fun Color.toHexRgb(): String {
    fun channel(v: Float) = (v * 255f).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(channel(red), channel(green), channel(blue))
}

/**
 * The pinned CDN URL of the widget for [deployment], or `null` when there is no
 * usable pin (empty CDN base / `widgetVersion` / `widgetIntegrity`).
 *
 * `DEVELOPMENT` is NOT excluded — it resolves to the same pinned CDN URL as every
 * other deployment (its CDN base defaults to the prod CDN, since the local backend
 * serves no widget and the SDK ships no bundled copy).
 *
 * The pins are baked from `.widget-version` + `.widget-integrity`, which
 * addressiq-web's fanout writes from the same build the CDN serves. The checked-in
 * default is the currently published pin, so a source/local build resolves a real
 * CDN URL out of the box. A `null` here means the flow fails closed — there is no
 * bundled fallback to inline.
 */
internal fun cdnWidgetUrl(deployment: AddressIQDeployment): String? = cdnWidgetUrl(
    cdnBaseUrl = deployment.defaultCdnUrl(),
    widgetVersion = AddressIQBuildConfig.widgetVersion,
    widgetIntegrity = AddressIQBuildConfig.widgetIntegrity,
)

/**
 * Pure form of [cdnWidgetUrl], so the preconditions are unit-testable.
 *
 * DEVELOPMENT is no longer excluded — a dev build loads the same pinned CDN
 * bundle as everything else (its cdnBaseUrl resolves to the prod CDN, since the
 * local backend serves no widget and the SDK ships no bundled copy).
 */
internal fun cdnWidgetUrl(
    cdnBaseUrl: String,
    widgetVersion: String,
    widgetIntegrity: String,
): String? {
    if (cdnBaseUrl.isBlank() || widgetVersion.isBlank() || widgetIntegrity.isBlank()) return null
    // The CDN serves immutable /v{x.y.z}/ paths — that immutability is what makes
    // pinning an SRI hash to one of them meaningful.
    return "${cdnBaseUrl.trimEnd('/')}/v$widgetVersion/iqcollect.js"
}

/** Error code reported when the pinned CDN widget fails to load. No fallback. */
internal const val WIDGET_LOAD_FAILED = "WIDGET_LOAD_FAILED"

private fun flowHtml(
    input: AddressIQVerifyInput,
    apiUrl: String,
    widgetUrl: String?,
): String {
    // Business identity is fetched by the widget from the backend (tenant behind
    // the API key). Only forward a client-supplied fallback name if provided.
    val cfg = JSONObject()
        .put("apiKey", input.apiKey)
        .put("apiUrl", apiUrl)
        .put("appUserId", input.appUserId)
        // Drives the platform-specific "Location permission" Settings screen.
        .put("platform", "android")

    // Business identity is fetched by the widget from the backend (tenant behind
    // the API key). A client-supplied name and theme colours override that. The
    // widget maps `business.primaryColor` / `secondaryColor` onto its CSS vars
    // (see addressiq-web `applyBrandingVars`), so forwarding the partner's
    // `AddressIQVerifyInput.theme` here is what makes the collect UI honour it
    // instead of silently rendering the widget's default accent.
    val theme = input.theme
    val business = JSONObject()
    if (input.businessName != null) business.put("displayName", input.businessName)
    theme?.primary?.let { business.put("primaryColor", it.toHexRgb()) }
    theme?.secondary?.let { business.put("secondaryColor", it.toHexRgb()) }
    if (business.length() > 0) cfg.put("business", business)

    return buildFlowHtml(
        cfgJson = cfg.toString(),
        widgetUrl = widgetUrl,
        cdnScriptUrl = cdnWidgetUrl(input.deployment),
        widgetIntegrity = AddressIQBuildConfig.widgetIntegrity,
    )
}

/**
 * Builds the page that mounts the widget. Pure — no Android, no Compose — so the
 * widget-sourcing decision below is unit-tested rather than emulator-tested.
 *
 * The SRI-pinned CDN copy is the ONLY source — the SDK ships no bundled widget.
 *
 * Precedence:
 *  1. [widgetUrl] — development-only override, loaded plainly (no SRI: a widget
 *     you are actively rebuilding cannot satisfy a fixed hash).
 *  2. [cdnScriptUrl] (see [cdnWidgetUrl]) — loaded with `integrity` +
 *     `crossorigin="anonymous"`. Chromium enforces SRI, so tampered bytes never
 *     execute. There is NO fallback: a CDN outage, an offline device, or an SRI
 *     mismatch fires onerror, which posts WIDGET_LOAD_FAILED through the native
 *     bridge ({kind:'event', name:'error'}) so the host sees an error rather than
 *     a blank WebView.
 *
 * With no usable pin and no override, fail closed rather than render a dead page.
 */
internal fun buildFlowHtml(
    cfgJson: String,
    widgetUrl: String?,
    cdnScriptUrl: String?,
    widgetIntegrity: String,
): String {
    val widgetScript = when {
        widgetUrl != null -> "<script src=\"$widgetUrl\"></script>"
        cdnScriptUrl != null -> {
            // `__iqWidgetLoadFailed` is DEFINED BEFORE the remote <script>: an
            // immediate failure (offline, SRI mismatch) would otherwise fire onerror
            // against an undefined function. The remote script is parser-blocking, so
            // its error event completes before the mount script runs.
            val onError = """
                <script>
                function __iqWidgetLoadFailed() {
                  var msg = { kind: 'event', name: 'error', payload: {
                    code: '$WIDGET_LOAD_FAILED',
                    message: 'AddressIQ: the widget could not be loaded from the CDN (outage, '
                      + 'no network, or a Subresource-Integrity mismatch). The SDK ships no '
                      + 'bundled copy, so there is nothing to fall back to.'
                  }};
                  try { window.AddressIQAndroid.postMessage(JSON.stringify(msg)); } catch (e) {}
                }
                </script>
            """.trimIndent()
            "$onError\n<script src=\"$cdnScriptUrl\" integrity=\"$widgetIntegrity\" " +
                "crossorigin=\"anonymous\" onerror=\"__iqWidgetLoadFailed()\"></script>"
        }
        else -> error(
            "AddressIQ: no pinned CDN widget build (empty version/integrity) and no " +
                "widgetUrl override — nothing safe to load. The SDK ships no bundled widget.",
        )
    }
    return """
    <!doctype html><html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
    <style>html,body{margin:0;height:100%;background:#fff}#mount{min-height:100%}</style>
    </head><body>
    <div id="mount"></div>
    $widgetScript
    <script>
      // Guarded: if the widget failed to load, window.AddressIQ is undefined and an
      // unguarded `new` would throw an opaque error masking WIDGET_LOAD_FAILED.
      if (window.AddressIQ && window.AddressIQ.IQCollect) {
        var cfg = $cfgJson;
        var c = new window.AddressIQ.IQCollect(document.getElementById('mount'), cfg);
        c.open();
      }
    </script>
    </body></html>
    """.trimIndent()
}
