package com.addressiq.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Development-only host + Maps-key overrides, baked from a gitignored
 * `local.properties` (or the environment) into `BuildConfig.ADDRESSIQ_DEV_*`.
 *
 * They exist because the DEVELOPMENT hosts are hardcoded to `10.0.2.2:4000` — an
 * EMULATOR alias for the host machine that a physical device cannot reach.
 *
 * The load-bearing property is the gate: an override is honoured ONLY in
 * DEVELOPMENT. Supplied anywhere else it throws — a build-time value must never
 * be able to point a shipped app at an arbitrary host, and a security-relevant
 * setting that silently does nothing is worse than a loud failure.
 */
class DevEnvOverrideTest {
    private val lan = "http://192.168.1.5:4000"

    @Test
    fun overrideIsHonouredInDevelopment() {
        assertEquals(
            lan,
            AddressIQDeployment.DEVELOPMENT.devOverride("ADDRESSIQ_DEV_API_URL", lan),
        )
    }

    @Test
    fun anUnsetOverrideIsNull() {
        // Empty means "not set", not "override with empty".
        assertNull(AddressIQDeployment.DEVELOPMENT.devOverride("ADDRESSIQ_DEV_API_URL", ""))
        assertNull(AddressIQDeployment.PRODUCTION.devOverride("ADDRESSIQ_DEV_API_URL", ""))
    }

    @Test
    fun anOverrideOnAShippedDeploymentThrows() {
        for (d in listOf(AddressIQDeployment.PRODUCTION, AddressIQDeployment.STAGING)) {
            val e = assertThrows(IllegalArgumentException::class.java) {
                d.devOverride("ADDRESSIQ_DEV_API_URL", lan)
            }
            assertEquals(true, e.message!!.contains("ADDRESSIQ_DEV_API_URL"))
            assertEquals(true, e.message!!.contains("development-only"))
        }
    }

    @Test
    fun aShippedBuildWithNoOverrideResolvesNormally() {
        // The throw must fire only when someone actually sets one — never on the
        // ordinary path. A published AAR bakes these as "".
        assertEquals(true, AddressIQDeployment.PRODUCTION.defaultApiUrl().startsWith("https://"))
        assertEquals("http://10.0.2.2:4000", AddressIQDeployment.DEVELOPMENT.defaultApiUrl())
    }
}
