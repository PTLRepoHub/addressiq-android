package com.addressiq.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The rule that decides whether an overnight reading exists at all.
 *
 * Geofence transitions are edge-triggered and DWELL is raised once per loiter,
 * so a resident asleep at home produces nothing between dusk and morning. The
 * periodic worker is what fills those hours, and this is the gate it runs
 * against: too strict and the backend's night-cycle floor can never be met, so
 * every verification on the device runs its window out as undecided; too loose
 * and a fix from yesterday evening gets reported as where the device is now.
 */
class BackgroundFixFreshnessTest {

    private val maxAgeMs = TimeUnit.MINUTES.toMillis(15)
    private val now = 1_772_000_000_000L

    @Test
    fun `a fix taken just now is fresh`() {
        assertTrue(AddressIQ.isFixFresh(now, now))
    }

    @Test
    fun `a fix inside the age limit is fresh`() {
        assertTrue(AddressIQ.isFixFresh(now - maxAgeMs + 1_000, now))
    }

    @Test
    fun `a fix exactly at the age limit is still fresh`() {
        // The boundary is inclusive; a run landing precisely on it should record
        // rather than discard, since the alternative is a lost night cycle.
        assertTrue(AddressIQ.isFixFresh(now - maxAgeMs, now))
    }

    @Test
    fun `a fix past the age limit is stale`() {
        assertFalse(AddressIQ.isFixFresh(now - maxAgeMs - 1, now))
    }

    @Test
    fun `an overnight cache is stale, which is what forces a current fix`() {
        // The case that matters: the device arrived home at 19:00 and the worker
        // finally runs at 02:00 under Doze. Seven hours old, so the cached fix
        // cannot be reported and the SDK has to ask for a current one.
        val sevenHours = TimeUnit.HOURS.toMillis(7)
        assertFalse(AddressIQ.isFixFresh(now - sevenHours, now))
    }

    @Test
    fun `a clock that jumped backwards does not read as stale`() {
        // A fix timestamped after `now` yields a negative age. It must not be
        // treated as expired, or an NTP correction mid-window would silently
        // drop readings.
        assertTrue(AddressIQ.isFixFresh(now + 5_000, now))
    }
}
