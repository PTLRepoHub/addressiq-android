package com.addressiq.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `startVerification` against a real backend, from a device.
 *
 * This is the call that crashed in the field (#28): every response was decoded
 * as `Map<String, String?>`, so `"isExisting": false` threw
 * `Unexpected JSON token ... at path: $['isExisting']` the moment a user
 * selected digital verification. It had shipped from the SDK's first commit.
 *
 * Nothing caught it because nothing exercised it. The unit suite now covers the
 * transport in isolation (`AddressIQApiClientTest` over MockWebServer), and
 * that is the right place for header and error-mapping detail. What it cannot
 * prove is the leg this test owns: that the SDK, configured the way an
 * integrator configures it, resolves a real host, parses what the real API
 * actually returns, and lights up collection afterwards. A stub server only
 * ever returns the body the test author imagined.
 *
 * Codes arrive as instrumentation arguments rather than baked in, so the test
 * cannot pass once and rot — see scripts/run-start-verification-test.sh.
 */
@RunWith(AndroidJUnit4::class)
class StartVerificationInstrumentedTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()

    private val locationCode get() = args.getString("aiqLocationCode") ?: ""
    private val apiKey get() = args.getString("aiqApiKey") ?: "aiq_test_demo_bank_seed01"

    /** DEVELOPMENT (local stack via 10.0.2.2) or STAGING, as in the live e2e test. */
    private val deployment: AddressIQDeployment
        get() = when (args.getString("aiqDeployment")?.uppercase()) {
            "STAGING" -> AddressIQDeployment.STAGING
            else -> AddressIQDeployment.DEVELOPMENT
        }

    @Test
    fun startsADigitalVerificationAndParsesTheResponse() {
        runBlocking {
            assumeFalse(
                "no location supplied — run via scripts/run-start-verification-test.sh",
                locationCode.isEmpty(),
            )

            AddressIQ.initialize(AddressIQConfig(apiKey = apiKey, deployment = deployment))
            AddressIQ.setUser(SdkUser(appUserId = "cust_start_verification_probe"))

            val result = AddressIQ.startVerification(context = context, locationCode = locationCode)

            // The regression itself: a boolean in the response body must not throw.
            // Asserting the key is PRESENT matters more than its value — a client
            // that cannot read the field at all is the bug, and `false` is the
            // value that used to blow up.
            assertTrue("response carried no isExisting", result.containsKey("isExisting"))
            assertTrue("isExisting was not a Boolean", result["isExisting"] is Boolean)

            val verificationCode = result["verificationCode"] as? String
            assertNotNull("no verificationCode in response", verificationCode)
            assertFalse("verificationCode was blank", verificationCode!!.isBlank())

            // startVerification activates collection on success; if it did not, the
            // parse succeeded but the SDK is not actually doing anything.
            assertEquals(AddressIQLifecycleState.COLLECTING, AddressIQ.getVerificationState().state)

            AddressIQ.cancelVerification(verificationCode)
        }
    }

    @Test
    fun listProvidersParsesAJsonArrayFromTheRealApi() {
        runBlocking {
            assumeFalse(
                "no location supplied — run via scripts/run-start-verification-test.sh",
                locationCode.isEmpty(),
            )

            AddressIQ.initialize(AddressIQConfig(apiKey = apiKey, deployment = deployment))

            // listProviders decoded through the same broken adapter as post(); a
            // provider row with any non-string field would have thrown identically.
            val providers = AddressIQ.listProviders()

            assertTrue("no providers returned", providers.isNotEmpty())
                assertTrue("provider row had no type", providers.first().containsKey("type"))
        }
    }
}
