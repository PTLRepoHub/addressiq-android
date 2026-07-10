package com.addressiq.android

import com.addressiq.android.ui.screens.WebFlowMessageRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the web-widget bridge protocol mapping. Guards that the Android
 * side stays in lockstep with the shared web `HostBridge` message shapes (the
 * same protocol the iOS WKWebView integration test exercises against the real
 * widget). No emulator / WebView needed — the router is pure.
 */
class WebFlowMessageRouterTest {

    private class Recorder {
        var completed: Pair<String, String?>? = null
        var cancelled = false
        var failed: Pair<String, String>? = null
        var locationRequestId: String? = null
        var permissionRequestId: String? = null
        var permissionStateId: String? = null
        var openSettingsId: String? = null
        var resolved: Pair<String, String>? = null
        var rejected: Triple<String, String, String>? = null
        var permission = "granted"

        fun router() = WebFlowMessageRouter(
            onCompleted = { code, addr -> completed = code to addr },
            onCancelled = { cancelled = true },
            onFailed = { code, msg -> failed = code to msg },
            onLocationRequest = { id -> locationRequestId = id },
            onPermissionRequest = { id -> permissionRequestId = id },
            onGetPermissionState = { id -> permissionStateId = id },
            onOpenSettings = { id -> openSettingsId = id },
            permissionStatus = { permission },
            resolveString = { id, value -> resolved = id to value },
            reject = { id, code, msg -> rejected = Triple(id, code, msg) },
        )
    }

    @Test
    fun addressSelectedEvent_completesWithLocationCode() {
        val r = Recorder()
        r.router().handle(
            """{"kind":"event","name":"addressSelected","payload":{"locationCode":"LOC-1","formattedAddress":"1 Marina, Lagos","geoPoint":{"lat":6.5,"lng":3.4}}}""",
        )
        assertEquals("LOC-1" to "1 Marina, Lagos", r.completed)
    }

    @Test
    fun verificationStartedEvent_completesTerminally() {
        val r = Recorder()
        r.router().handle("""{"kind":"event","name":"verificationStarted","payload":{"locationCode":"LOC-9","verificationId":"ver_1"}}""")
        assertEquals("LOC-9", r.completed?.first)
        // No formattedAddress on this event → null, not empty string.
        assertNull(r.completed?.second)
    }

    @Test
    fun closeEvent_cancels() {
        val r = Recorder()
        r.router().handle("""{"kind":"event","name":"close"}""")
        assertTrue(r.cancelled)
    }

    @Test
    fun errorEvent_failsWithCodeAndMessage() {
        val r = Recorder()
        r.router().handle("""{"kind":"event","name":"error","payload":{"code":"HTTP_500","message":"boom"}}""")
        assertEquals("HTTP_500" to "boom", r.failed)
    }

    @Test
    fun getPermissionStatusRequest_resolvesWithMappedPermission() {
        val r = Recorder().apply { permission = "denied" }
        r.router().handle("""{"kind":"request","id":"req_1","action":"getPermissionStatus"}""")
        assertEquals("req_1" to "denied", r.resolved)
    }

    @Test
    fun getLocationRequest_delegatesToLocationHandler() {
        val r = Recorder()
        r.router().handle("""{"kind":"request","id":"req_2","action":"getLocation"}""")
        assertEquals("req_2", r.locationRequestId)
    }

    @Test
    fun requestPermission_delegatesToPermissionHandler() {
        val r = Recorder()
        r.router().handle("""{"kind":"request","id":"req_4","action":"requestPermission"}""")
        assertEquals("req_4", r.permissionRequestId)
    }

    @Test
    fun getPermissionState_and_openSettings_delegate() {
        val r = Recorder()
        r.router().handle("""{"kind":"request","id":"req_5","action":"getPermissionState"}""")
        assertEquals("req_5", r.permissionStateId)
        r.router().handle("""{"kind":"request","id":"req_6","action":"openSettings"}""")
        assertEquals("req_6", r.openSettingsId)
    }

    @Test
    fun unknownAction_rejects() {
        val r = Recorder()
        r.router().handle("""{"kind":"request","id":"req_3","action":"teleport"}""")
        assertEquals("req_3", r.rejected?.first)
        assertEquals("UNKNOWN_ACTION", r.rejected?.second)
    }

    @Test
    fun malformedMessage_isIgnored() {
        val r = Recorder()
        r.router().handle("not json")
        assertNull(r.completed); assertNull(r.failed); assertNull(r.locationRequestId)
    }

    @Test
    fun webPermissionMapping_coversTheSet() {
        assertEquals("granted", WebFlowMessageRouter.webPermission("GRANTED"))
        assertEquals("denied", WebFlowMessageRouter.webPermission("BLOCKED"))
        assertEquals("denied", WebFlowMessageRouter.webPermission("DENIED"))
        assertEquals("prompt", WebFlowMessageRouter.webPermission("NOT_DETERMINED"))
        assertEquals("unknown", WebFlowMessageRouter.webPermission("WHATEVER"))
    }
}
