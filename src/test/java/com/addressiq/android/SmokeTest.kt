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

    @Test
    fun deploymentsResolveDistinctCdnUrls() {
        val staging = AddressIQDeployment.STAGING.defaultCdnUrl()
        val production = AddressIQDeployment.PRODUCTION.defaultCdnUrl()

        assertTrue(staging.startsWith("https://"))
        assertTrue(production.startsWith("https://"))
        assertEquals("staging and production cdn must differ", false, staging == production)
        // The CDN is a resolved config value only — nothing fetches from it
        // (the widget ships bundled in assets/iqcollect.js and fails closed).
        assertEquals(
            "production cdn and api hosts must differ",
            false,
            production == AddressIQDeployment.PRODUCTION.defaultApiUrl(),
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
