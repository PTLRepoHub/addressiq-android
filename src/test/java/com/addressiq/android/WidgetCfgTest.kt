package com.addressiq.android

import com.addressiq.android.ui.screens.widgetCfgJson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the WebView actually hands `new AddressIQ.IQCollect(mount, cfg)`.
 *
 * The widget resolves its own API/ingest hosts from an ENVIRONMENT NAME
 * (`resolveEnvironmentUrls`); it never reads a URL out of its config, and an
 * absent `environment` silently defaults it to production. So a STAGING build
 * used to load the staging bundle off the staging CDN and then call the
 * PRODUCTION API — the deployment was honoured everywhere except the requests
 * that actually carry data. Nothing covered this config object before.
 */
class WidgetCfgTest {

    @Test
    fun tellsTheWidgetWhichEnvironmentToCall() {
        for (deployment in AddressIQDeployment.values()) {
            val json = widgetCfgJson(
                apiKey = "pk_1",
                deployment = deployment,
                appUserId = "u1",
            )
            assertTrue(
                "missing environment for $deployment: $json",
                json.contains("\"environment\":\"${deployment.name.lowercase()}\""),
            )
        }
    }

    @Test
    fun neverHandsTheWidgetAHostUrl() {
        // A URL here is silently ignored by the widget, which is exactly how the
        // production-API-from-staging bug stayed invisible.
        val json = widgetCfgJson(
            apiKey = "pk_1",
            deployment = AddressIQDeployment.STAGING,
            appUserId = "u1",
        )
        assertFalse(json, json.contains("apiUrl"))
    }

    @Test
    fun forwardsBusinessOverridesOnlyWhenSupplied() {
        val bare = widgetCfgJson("pk_1", AddressIQDeployment.STAGING, "u1")
        assertFalse(bare, bare.contains("business"))

        val themed = widgetCfgJson(
            apiKey = "pk_1",
            deployment = AddressIQDeployment.STAGING,
            appUserId = "u1",
            businessName = "Kuda",
            primaryHex = "#24eb5f",
        )
        assertTrue(themed, themed.contains("\"displayName\":\"Kuda\""))
        assertTrue(themed, themed.contains("\"primaryColor\":\"#24eb5f\""))
    }
}
