package com.addressiq.android.ui.screens

import org.json.JSONObject

/**
 * Pure (Android-framework-free) protocol logic for the web-widget bridge.
 *
 * Decodes the `HostBridge` messages the shared widget posts and dispatches them
 * to injected sinks. Kept free of `WebView`/`Activity` so the exact wire mapping
 * — the thing that must stay in lockstep with the web `HostBridge` — is unit
 * testable on the JVM without an emulator. [WebFlowBridge] wires the sinks to
 * the real WebView + permission layer.
 *
 * JS → native:
 *   { kind: 'event',   name, payload }
 *   { kind: 'request', id, action, payload }
 */
internal class WebFlowMessageRouter(
    private val onCompleted: (locationCode: String, formattedAddress: String?) -> Unit,
    private val onCancelled: () -> Unit,
    private val onFailed: (code: String, message: String) -> Unit,
    /** Async location request — the caller runs the permission prompt + fix, then replies by id. */
    private val onLocationRequest: (id: String) -> Unit,
    /** Async Always+Precise permission prompt — the caller runs it, then replies by id. */
    private val onPermissionRequest: (id: String) -> Unit,
    /** Read current grant WITHOUT prompting — caller replies { foreground, background } by id. */
    private val onGetPermissionState: (id: String) -> Unit,
    /** Open the OS app-settings page — caller replies by id. */
    private val onOpenSettings: (id: String) -> Unit,
    /** Returns the current web-permission string ('granted'|'denied'|'prompt'|'unknown'). */
    private val permissionStatus: () -> String,
    private val resolveString: (id: String, value: String) -> Unit,
    private val reject: (id: String, code: String, message: String) -> Unit,
) {
    fun handle(message: String) {
        val obj = runCatching { JSONObject(message) }.getOrNull() ?: return
        when (obj.optString("kind")) {
            "event" -> handleEvent(obj.optString("name"), obj.optJSONObject("payload"))
            "request" -> handleRequest(obj.optString("id"), obj.optString("action"))
        }
    }

    private fun handleEvent(name: String, payload: JSONObject?) {
        when (name) {
            "addressSelected", "verificationStarted" -> {
                val code = payload?.optString("locationCode").orEmpty()
                val addr = payload?.optString("formattedAddress")?.ifEmpty { null }
                onCompleted(code, addr)
            }
            "close" -> onCancelled()
            "error" -> onFailed(
                payload?.optString("code")?.ifEmpty { null } ?: "WIDGET_ERROR",
                payload?.optString("message")?.ifEmpty { null } ?: "Widget reported an error",
            )
        }
    }

    private fun handleRequest(id: String, action: String) {
        if (id.isEmpty()) return
        when (action) {
            "getPermissionStatus" -> resolveString(id, permissionStatus())
            "getLocation" -> onLocationRequest(id)
            "requestPermission" -> onPermissionRequest(id)
            "getPermissionState" -> onGetPermissionState(id)
            "openSettings" -> onOpenSettings(id)
            else -> reject(id, "UNKNOWN_ACTION", "Unsupported bridge action: $action")
        }
    }

    companion object {
        /** Map the SDK's foreground-permission state to the web `LocationPermission` set. */
        fun webPermission(foreground: String): String = when (foreground) {
            "GRANTED" -> "granted"
            "BLOCKED", "DENIED" -> "denied"
            "NOT_DETERMINED" -> "prompt"
            else -> "unknown"
        }
    }
}
