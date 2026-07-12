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
import com.addressiq.android.AddressIQEnvironment
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
    /** Explicit developer override. `null` means "resolve normally": pinned CDN build, bundled asset as fallback. */
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
                // The bundled asset is always read if present: it is either the only
                // widget source (no pinned CDN build) or the onerror fallback.
                val bundled = runCatching {
                    ctx.assets.open("iqcollect.js").bufferedReader().use { it.readText() }
                }.getOrNull()
                loadDataWithBaseURL(apiUrl, flowHtml(input, apiUrl, widgetUrl, bundled), "text/html", "utf-8", null)
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
 * The pinned CDN URL of the widget for [environment], or `null` when the CDN
 * path is unavailable and the bundled asset must be used.
 *
 * Unavailable means: `DEVELOPMENT` (the CDN URL there is the dev host, which
 * serves no versioned widget), or any of the CDN base URL / `widgetVersion` /
 * `widgetIntegrity` not being baked in. The last case is the norm until
 * addressiq-web's fanout writes `.widget-version` + `.widget-integrity` — the
 * SDK is bundled-only until then, by construction rather than by accident.
 */
internal fun cdnWidgetUrl(environment: AddressIQEnvironment): String? = cdnWidgetUrl(
    isDevelopment = environment == AddressIQEnvironment.DEVELOPMENT,
    cdnBaseUrl = environment.defaultCdnUrl(),
    widgetVersion = AddressIQBuildConfig.widgetVersion,
    widgetIntegrity = AddressIQBuildConfig.widgetIntegrity,
)

/** Pure form of [cdnWidgetUrl], so the preconditions are unit-testable. */
internal fun cdnWidgetUrl(
    isDevelopment: Boolean,
    cdnBaseUrl: String,
    widgetVersion: String,
    widgetIntegrity: String,
): String? {
    if (isDevelopment) return null
    if (cdnBaseUrl.isBlank() || widgetVersion.isBlank() || widgetIntegrity.isBlank()) return null
    // The CDN serves immutable /v{x.y.z}/ paths — that immutability is what makes
    // pinning an SRI hash to one of them meaningful.
    return "${cdnBaseUrl.trimEnd('/')}/v$widgetVersion/iqcollect.js"
}

private fun flowHtml(
    input: AddressIQVerifyInput,
    apiUrl: String,
    widgetUrl: String?,
    bundledJs: String?,
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
        bundledJs = bundledJs,
        cdnScriptUrl = cdnWidgetUrl(input.environment),
        widgetIntegrity = AddressIQBuildConfig.widgetIntegrity,
    )
}

/**
 * Builds the page that mounts the widget. Pure — no Android, no Compose — so the
 * widget-sourcing decision below is unit-tested rather than emulator-tested.
 *
 * Precedence:
 *  1. [widgetUrl] — explicit developer override, loaded plainly (no SRI: the
 *     developer chose the host, and a local dev bundle has no stable hash).
 *  2. [cdnScriptUrl] (see [cdnWidgetUrl]) — loaded with `integrity` +
 *     `crossorigin="anonymous"`, with [bundledJs] wired up as the `onerror`
 *     fallback. Chromium enforces SRI, so tampered bytes never execute; they
 *     fire onerror and we fall back to the vendored bundle. Same for a CDN
 *     outage or an offline device.
 *  3. [bundledJs] inline — the only source when there is no pinned CDN build.
 *
 * With none of the three, fail closed rather than render a dead page.
 */
internal fun buildFlowHtml(
    cfgJson: String,
    widgetUrl: String?,
    bundledJs: String?,
    cdnScriptUrl: String?,
    widgetIntegrity: String,
): String {
    val widgetScript = when {
        widgetUrl != null -> "<script src=\"$widgetUrl\"></script>"
        cdnScriptUrl != null -> {
            // `__iqWidgetFallback` is DEFINED BEFORE the remote <script>: an immediate
            // failure (offline, SRI mismatch) would otherwise fire onerror against an
            // undefined function. The remote script is parser-blocking, so its error
            // event — and therefore the synchronous inline injection below — completes
            // before the mount script runs, and window.AddressIQ is there either way.
            val fallbackBody = if (bundledJs == null) {
                // No bundle vendored: nothing to fall back to. Surface it loudly.
                "throw new Error('AddressIQ: widget bundle unavailable');"
            } else {
                """
                var s = document.createElement('script');
                s.text = ${JSONObject.quote(bundledJs)};
                document.head.appendChild(s);
                """.trimIndent()
            }
            "<script>\nfunction __iqWidgetFallback() {\n$fallbackBody\n}\n</script>\n" +
                "<script src=\"$cdnScriptUrl\" integrity=\"$widgetIntegrity\" " +
                "crossorigin=\"anonymous\" onerror=\"__iqWidgetFallback()\"></script>"
        }
        bundledJs != null -> "<script>$bundledJs</script>"
        else -> error(
            "AddressIQ: bundled widget (assets/iqcollect.js) missing, no pinned CDN build, " +
                "and no widgetUrl override.",
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
      var cfg = $cfgJson;
      var c = new window.AddressIQ.IQCollect(document.getElementById('mount'), cfg);
      c.open();
    </script>
    </body></html>
    """.trimIndent()
}
