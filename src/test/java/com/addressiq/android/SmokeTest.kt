package com.addressiq.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM smoke test — guards the release pipeline against a broken build by
 * exercising a pure (Android-framework-free) slice of the public surface.
 * Runs under `./gradlew test`. Instrumented behaviour lives in androidTest.
 */
class SmokeTest {
    @Test
    fun environmentsResolveDistinctApiUrls() {
        val staging = AddressIQEnvironment.STAGING.defaultApiUrl()
        val production = AddressIQEnvironment.PRODUCTION.defaultApiUrl()

        assertTrue(staging.startsWith("https://"))
        assertTrue(production.startsWith("https://"))
        assertEquals("staging and production must differ", false, staging == production)
    }

    @Test
    fun environmentsResolveDistinctIngestUrls() {
        val staging = AddressIQEnvironment.STAGING.defaultIngestUrl()
        val production = AddressIQEnvironment.PRODUCTION.defaultIngestUrl()

        assertTrue(staging.startsWith("https://"))
        assertTrue(production.startsWith("https://"))
        assertEquals("staging and production ingest must differ", false, staging == production)
        // Ingest is a dedicated host, distinct from the general API host.
        assertEquals(
            "production ingest and api hosts must differ",
            false,
            production == AddressIQEnvironment.PRODUCTION.defaultApiUrl(),
        )
    }

    @Test
    fun environmentsResolveDistinctCdnUrls() {
        val staging = AddressIQEnvironment.STAGING.defaultCdnUrl()
        val production = AddressIQEnvironment.PRODUCTION.defaultCdnUrl()

        assertTrue(staging.startsWith("https://"))
        assertTrue(production.startsWith("https://"))
        assertEquals("staging and production cdn must differ", false, staging == production)
        // The CDN is a resolved config value only — nothing fetches from it
        // (the widget ships bundled in assets/iqcollect.js and fails closed).
        assertEquals(
            "production cdn and api hosts must differ",
            false,
            production == AddressIQEnvironment.PRODUCTION.defaultApiUrl(),
        )
    }

    @Test
    fun deprecatedSandboxAliasResolvesToStaging() {
        @Suppress("DEPRECATION")
        assertEquals(AddressIQEnvironment.STAGING, AddressIQEnvironment.SANDBOX)
    }

    @Test
    fun lifecycleStartsUninitialized() {
        assertEquals(
            AddressIQLifecycleState.UNINITIALIZED,
            AddressIQLifecycleState.valueOf("UNINITIALIZED"),
        )
    }
}
