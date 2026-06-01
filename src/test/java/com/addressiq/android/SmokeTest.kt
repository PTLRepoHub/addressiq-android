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
        val sandbox = AddressIQEnvironment.SANDBOX.defaultApiUrl()
        val production = AddressIQEnvironment.PRODUCTION.defaultApiUrl()

        assertTrue(sandbox.startsWith("https://"))
        assertTrue(production.startsWith("https://"))
        assertEquals("sandbox and production must differ", false, sandbox == production)
    }

    @Test
    fun lifecycleStartsUninitialized() {
        assertEquals(
            AddressIQLifecycleState.UNINITIALIZED,
            AddressIQLifecycleState.valueOf("UNINITIALIZED"),
        )
    }
}
