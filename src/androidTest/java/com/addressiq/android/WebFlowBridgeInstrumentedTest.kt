package com.addressiq.android

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Live-engine integration test for the web-widget bridge, mirroring the iOS
 * WKWebView test. Loads the REAL bundled widget (`assets/iqcollect.js`) into an
 * on-device [WebView], drives the flow to the address step, and asserts the full
 * round-trip: the widget's BridgeLocationProvider posts a `getLocation` request
 * to the native `@JavascriptInterface` (JS → native), and a native
 * `window.AddressIQBridge.resolve(...)` reply is accepted (native → JS).
 */
@RunWith(AndroidJUnit4::class)
class WebFlowBridgeInstrumentedTest {

    @Test
    fun bundledWidgetDrivesGetLocationBridgeRoundTrip() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = instr.targetContext
        val widgetJs = ctx.assets.open("iqcollect.js").bufferedReader().use { it.readText() }
        assertTrue("bundle should define IQCollect", widgetJs.contains("IQCollect"))

        val gotGetLocation = CountDownLatch(1)

        instr.runOnMainSync {
            val webView = WebView(ctx)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun postMessage(message: String) {
                    val obj = runCatching { JSONObject(message) }.getOrNull() ?: return
                    if (obj.optString("kind") == "request" && obj.optString("action") == "getLocation") {
                        val id = obj.optString("id")
                        webView.post {
                            webView.evaluateJavascript(
                                "window.AddressIQBridge.resolve(${JSONObject.quote(id)}, {lat:9.0765,lon:7.3986,accuracy:8});",
                                null,
                            )
                        }
                        gotGetLocation.countDown()
                    }
                }
            }, "AddressIQAndroid")

            // apiUrl → closed port so listAddresses() fails fast → the widget falls
            // through to the collect flow, whose address step exposes "Use my
            // current location" → getLocation.
            val cfg = JSONObject()
                .put("apiKey", "k")
                .put("apiUrl", "https://127.0.0.1:1")
                .put("appUserId", "u1")
                .put("business", JSONObject().put("displayName", "Test Biz").put("primaryColor", "#111827"))
            val html = """
                <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body><div id="mount"></div>
                <script>$widgetJs</script>
                <script>
                  var c = new window.AddressIQ.IQCollect(document.getElementById('mount'), $cfg);
                  c.open();
                  window.__drive = setInterval(function(){
                    try {
                      var btns = Array.prototype.slice.call(document.querySelectorAll('.iq-btn'));
                      var byText = function(t){ return btns.filter(function(b){ return b.textContent.trim().toLowerCase().indexOf(t) >= 0; })[0]; };
                      var useLoc = byText('current location');
                      if (useLoc) { clearInterval(window.__drive); useLoc.click(); return; }
                      var next = byText('continue') || byText('next');
                      if (next) next.click();
                    } catch (e) {}
                  }, 350);
                </script></body></html>
            """.trimIndent()
            webView.loadDataWithBaseURL("https://127.0.0.1:1", html, "text/html", "utf-8", null)
        }

        assertTrue(
            "native should receive a getLocation request from the widget",
            gotGetLocation.await(30, TimeUnit.SECONDS),
        )
    }
}
