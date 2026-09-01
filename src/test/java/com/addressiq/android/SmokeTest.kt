package com.addressiq.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM smoke test — guards the release pipeline against a broken build by
 * exercising a pure (Android-framework-free) slice of the public surface.
 * Runs under `./gradlew test`. Instrumented behaviour lives in androidTest.
 */
class SmokeTest {
    @Test
    fun deploymentsResolveDistinctApiUrls() {
        val staging = AddressIQDeployment.STAGING.defaultApiUrl()
        val production = AddressIQDeployment.PRODUCTION.defaultApiUrl()

        assertTrue(staging.startsWith("https://"))
        assertTrue(production.startsWith("https://"))
        assertEquals("staging and production must differ", false, staging == production)
    }

    @Test
    fun deploymentsResolveDistinctIngestUrls() {
        val staging = AddressIQDeployment.STAGING.defaultIngestUrl()
        val production = AddressIQDeployment.PRODUCTION.defaultIngestUrl()

        assertTrue(staging.startsWith("https://"))
        assertTrue(production.startsWith("https://"))
        assertEquals("staging and production ingest must differ", false, staging == production)
        // Ingest is a dedicated host, distinct from the general API host.
        assertEquals(
            "production ingest and api hosts must differ",
            false,
            production == AddressIQDeployment.PRODUCTION.defaultApiUrl(),
        )
    }

    /**
     * Every deployment fetches the widget from the production CDN.
     *
     * This used to assert the opposite — that staging and production resolve to
     * *different* CDNs. That was wrong once the vendored widget was deleted: the
     * SDK pins one SRI hash, baked from the production build, and the staging
     * bundle is deliberately not byte-identical (different Maps key), so a staging
     * fetch can only ever 404 or fail integrity. There is no bundled copy left to
     * fall back to, so it surfaces as WIDGET_LOAD_FAILED.
     */
    @Test
    fun everyDeploymentFetchesTheWidgetFromTheProductionCdn() {
        val production = AddressIQDeployment.PRODUCTION.defaultCdnUrl()

        assertTrue(production.startsWith("https://"))
        for (deployment in AddressIQDeployment.entries) {
            assertEquals(
                "${deployment.name} must fetch the widget from the pinned production CDN",
                production,
                deployment.defaultCdnUrl(),
            )
        }
    }

    /**
     * Pinning the CDN must not have flattened the deployment axis itself — the API
     * host is still per-deployment (asserted above), and the CDN is a separate host
     * from it.
     */
    @Test
    fun theWidgetCdnIsNotTheApiHost() {
        assertEquals(
            "production cdn and api hosts must differ",
            false,
            AddressIQDeployment.PRODUCTION.defaultCdnUrl() ==
                AddressIQDeployment.PRODUCTION.defaultApiUrl(),
        )
    }

    /**
     * `SANDBOX` used to exist as a companion alias for `STAGING`, which asserted
     * that sandbox was a deployment. It is not — sandbox-vs-production is a
     * property of the API key, resolved server-side. The alias is gone, so
     * `valueOf("SANDBOX")` throws rather than silently selecting the staging hosts.
     */
    @Test
    fun sandboxIsNotADeployment() {
        assertEquals(
            listOf("STAGING", "PRODUCTION", "DEVELOPMENT"),
            AddressIQDeployment.entries.map { it.name },
        )
        assertThrows(IllegalArgumentException::class.java) {
            AddressIQDeployment.valueOf("SANDBOX")
        }
    }

    @Test
    fun lifecycleStartsUninitialized() {
        assertEquals(
            AddressIQLifecycleState.UNINITIALIZED,
            AddressIQLifecycleState.valueOf("UNINITIALIZED"),
        )
    }
}
