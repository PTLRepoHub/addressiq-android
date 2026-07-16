package com.addressiq.android

import com.addressiq.android.ui.screens.buildFlowHtml
import com.addressiq.android.ui.screens.cdnWidgetUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the verify WebView gets the widget from.
 *
 * The SRI-pinned CDN copy is now the ONLY source — the SDK ships no bundled
 * widget. Several of these tests previously asserted the opposite (bundle as
 * fallback, DEVELOPMENT inlines it, an unbaked pin inlines it); they are inverted,
 * not deleted. The HTML builder is pure, so this needs no WebView.
 */
class WidgetSourcingTest {

    private val cfg = """{"apiKey":"pk_1"}"""
    private val integrity = "sha384-abc123"

    private fun html(
        widgetUrl: String? = null,
        cdnBaseUrl: String = "https://cdn.addressiqpro.com",
        version: String = "0.4.0",
        sri: String = integrity,
    ) = buildFlowHtml(
        cfgJson = cfg,
        widgetUrl = widgetUrl,
        cdnScriptUrl = cdnWidgetUrl(cdnBaseUrl, version, sri),
        widgetIntegrity = sri,
    )

    // ── cdnWidgetUrl preconditions ──

    @Test
    fun cdnUrl_isImmutableVersionedPath() {
        assertEquals(
            "https://cdn.addressiqpro.com/v0.4.0/iqcollect.js",
            cdnWidgetUrl("https://cdn.addressiqpro.com/", "0.4.0", integrity),
        )
    }

    @Test
    fun cdnUrl_nullWhenPreconditionsUnmet() {
        assertNull(cdnWidgetUrl("", "0.4.0", integrity)) // no cdn
        assertNull(cdnWidgetUrl("https://cdn.addressiqpro.com", "", integrity)) // no version
        assertNull(cdnWidgetUrl("https://cdn.addressiqpro.com", "0.4.0", "")) // no integrity
    }

    /** The checked-in build config now carries the real pin, so a deployment resolves. */
    @Test
    fun cdnUrl_worksOutOfTheBoxWithTheBakedPin() {
        // PRODUCTION resolves to the prod CDN + the baked v0.5.3 pin.
        assertTrue(cdnWidgetUrl(AddressIQDeployment.PRODUCTION)!!.contains("/v0.5.3/iqcollect.js"))
        // DEVELOPMENT is no longer excluded — it resolves too (to the prod CDN).
        assertTrue(cdnWidgetUrl(AddressIQDeployment.DEVELOPMENT)!!.contains("/iqcollect.js"))
    }

    // ── HTML shape ──

    @Test
    fun loadsPinnedRemoteScript_withNoFallback() {
        val h = html()
        assertTrue(
            h.contains(
                """<script src="https://cdn.addressiqpro.com/v0.4.0/iqcollect.js" """ +
                    """integrity="sha384-abc123" crossorigin="anonymous" onerror="__iqWidgetLoadFailed()"></script>""",
            ),
        )
        // No vendored fallback, and a failed load is reported.
        assertFalse(h.contains("__iqWidgetFallback"))
        assertFalse(h.contains("document.head.appendChild(s)"))
        assertTrue(h.contains(WIDGET_LOAD_FAILED_LITERAL))
        assertTrue(h.contains("window.AddressIQAndroid.postMessage"))
        // onerror handler defined before the remote script it guards.
        assertTrue(h.indexOf("function __iqWidgetLoadFailed()") < h.indexOf("<script src="))
    }

    @Test
    fun explicitWidgetUrl_winsOverCdn_unpinned() {
        val h = html(widgetUrl = "http://10.0.2.2:5173/iqcollect.js")
        assertTrue(h.contains("""<script src="http://10.0.2.2:5173/iqcollect.js"></script>"""))
        assertFalse(h.contains("cdn.addressiqpro.com"))
        assertFalse(h.contains("integrity="))
    }

    @Test
    fun noPinAndNoOverride_failsClosed() {
        // Previously this inlined the bundle. Now there is nothing to inline, and an
        // unpinned remote script would be RCE — so it throws.
        assertTrue(runCatching { html(version = "") }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { html(sri = "") }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun bootScriptIsGuarded_soAFailedLoadDoesNotThrowOverTheError() {
        assertTrue(html().contains("if (window.AddressIQ && window.AddressIQ.IQCollect)"))
    }

    private companion object {
        const val WIDGET_LOAD_FAILED_LITERAL = "WIDGET_LOAD_FAILED"
    }
}
