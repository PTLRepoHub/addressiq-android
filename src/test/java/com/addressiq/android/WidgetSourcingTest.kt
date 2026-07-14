package com.addressiq.android

import com.addressiq.android.ui.screens.buildFlowHtml
import com.addressiq.android.ui.screens.cdnWidgetUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the verify WebView gets the widget from. CDN-first with an SRI pin,
 * bundled asset as the fallback, fail-closed with neither. The HTML builder is
 * pure, so this needs no WebView.
 */
class WidgetSourcingTest {

    private val cfg = """{"apiKey":"pk_1"}"""
    private val bundle = "window.AddressIQ={};"
    private val integrity = "sha384-abc123"

    private fun html(
        widgetUrl: String? = null,
        bundledJs: String? = bundle,
        isDevelopment: Boolean = false,
        cdnBaseUrl: String = "https://cdn.addressiqpro.com",
        version: String = "0.4.0",
        sri: String = integrity,
    ) = buildFlowHtml(
        cfgJson = cfg,
        widgetUrl = widgetUrl,
        bundledJs = bundledJs,
        cdnScriptUrl = cdnWidgetUrl(isDevelopment, cdnBaseUrl, version, sri),
        widgetIntegrity = sri,
    )

    // ── cdnWidgetUrl preconditions ──

    @Test
    fun cdnUrl_isImmutableVersionedPath() {
        assertEquals(
            "https://cdn.addressiqpro.com/v0.4.0/iqcollect.js",
            cdnWidgetUrl(false, "https://cdn.addressiqpro.com/", "0.4.0", integrity),
        )
    }

    @Test
    fun cdnUrl_nullWhenPreconditionsUnmet() {
        assertNull(cdnWidgetUrl(true, "https://cdn.addressiqpro.com", "0.4.0", integrity)) // DEVELOPMENT
        assertNull(cdnWidgetUrl(false, "", "0.4.0", integrity)) // no cdn
        assertNull(cdnWidgetUrl(false, "https://cdn.addressiqpro.com", "", integrity)) // no version
        assertNull(cdnWidgetUrl(false, "https://cdn.addressiqpro.com", "0.4.0", "")) // no integrity
    }

    /** The checked-in build config has no widget files baked yet → bundled-only. */
    @Test
    fun cdnUrl_isDormantUntilFanoutWritesTheFiles() {
        assertNull(cdnWidgetUrl(AddressIQDeployment.PRODUCTION))
    }

    // ── HTML shape ──

    @Test
    fun cdnPreconditionsMet_loadsPinnedRemoteScriptWithBundledFallback() {
        val h = html()
        assertTrue(
            h.contains(
                """<script src="https://cdn.addressiqpro.com/v0.4.0/iqcollect.js" """ +
                    """integrity="sha384-abc123" crossorigin="anonymous" onerror="__iqWidgetFallback()"></script>""",
            ),
        )
        // Fallback defined before the remote script, and it carries the bundle.
        assertTrue(h.indexOf("function __iqWidgetFallback()") < h.indexOf("<script src="))
        assertTrue(h.contains("document.head.appendChild(s)"))
        assertTrue(h.contains("window.AddressIQ={};"))
    }

    @Test
    fun development_inlinesBundle_andNeverLoadsRemotely() {
        val h = html(isDevelopment = true)
        assertTrue(h.contains("<script>$bundle</script>"))
        assertFalse(h.contains("<script src="))
        assertFalse(h.contains("integrity="))
    }

    @Test
    fun emptyVersionOrIntegrity_inlinesBundle() {
        for (h in listOf(html(version = ""), html(sri = ""))) {
            assertTrue(h.contains("<script>$bundle</script>"))
            assertFalse(h.contains("<script src="))
            assertFalse(h.contains("__iqWidgetFallback"))
        }
    }

    @Test
    fun explicitWidgetUrl_winsOverCdnAndBundle() {
        val h = html(widgetUrl = "http://10.0.2.2:5173/iqcollect.js")
        assertTrue(h.contains("""<script src="http://10.0.2.2:5173/iqcollect.js"></script>"""))
        assertFalse(h.contains("cdn.addressiqpro.com"))
        assertFalse(h.contains(bundle))
    }

    @Test
    fun noBundleAndNoCdnAndNoOverride_failsClosed() {
        val e = runCatching { html(bundledJs = null, version = "") }.exceptionOrNull()
        assertTrue(e is IllegalStateException)
    }

    @Test
    fun cfgIsMountedInEveryMode() {
        listOf(html(), html(isDevelopment = true), html(widgetUrl = "https://x/w.js")).forEach {
            assertTrue(it.contains("var cfg = $cfg;"))
        }
    }
}
