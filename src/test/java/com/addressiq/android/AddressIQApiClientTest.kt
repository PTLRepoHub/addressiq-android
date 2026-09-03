package com.addressiq.android

import com.addressiq.android.network.AddressIQApiClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The transport, driven over a real socket.
 *
 * This is the coverage the SDK did not have when the `isExisting` crash shipped
 * (PR #28): `post()` was private, built its URL from build-time constants, and
 * held a private OkHttpClient, so nothing could reach it. [AddressIQApiClient]
 * takes its base URL as a constructor parameter for exactly this reason — no
 * test-only seam in production code, the test just passes a URL.
 */
class AddressIQApiClientTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun client() = AddressIQApiClient(
        apiKey = "test-key",
        baseUrl = server.url("/").toString().trimEnd('/'),
    )

    @Test
    fun `parses the digital verification response that used to crash`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"verificationCode":"VER_123","locationCode":"LOC_EIMZ3AK8C5XRZBV2",
                   "status":"PENDING","isExisting":false}""",
            ),
        )

        val result = client().post(
            "/api/v1/locations/LOC_EIMZ3AK8C5XRZBV2/verifications/digital",
            mapOf("digitalProvider" to "internal_ai"),
        )

        assertEquals("VER_123", result["verificationCode"])
        assertEquals(false, result["isExisting"])
        assertEquals(
            "/api/v1/locations/LOC_EIMZ3AK8C5XRZBV2/verifications/digital",
            server.takeRequest().path,
        )
    }

    @Test
    fun `sends booleans and numbers as JSON types on the wire`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        client().post(
            "/api/v1/locations/LOC_1/verifications/combined",
            mapOf("physicalProvider" to "partner", "startDigital" to true, "slaHours" to 24),
        )

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(true, body.get("startDigital"))
        assertEquals(24, body.get("slaHours"))
        assertEquals("partner", body.get("physicalProvider"))
    }

    @Test
    fun `sets the auth, idempotency and branch headers`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        client().post("/api/v1/x", emptyMap(), branchId = "BR_9")

        val req = server.takeRequest()
        assertEquals("test-key", req.getHeader("x-api-key"))
        assertEquals("BR_9", req.getHeader("x-branch-id"))
        assertTrue(req.getHeader("idempotency-key").orEmpty().startsWith("iqidem_android_"))
        // OkHttp appends "; charset=utf-8" to the media type it was given.
        assertTrue(req.getHeader("Content-Type").orEmpty().startsWith("application/json"))
    }

    @Test
    fun `honours a caller-supplied idempotency key and omits an absent branch`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        client().post("/api/v1/x", emptyMap(), idempotencyKey = "caller-key")

        val req = server.takeRequest()
        assertEquals("caller-key", req.getHeader("idempotency-key"))
        assertNull(req.getHeader("x-branch-id"))
    }

    @Test
    fun `surfaces the server's own error code and message on 4xx`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"code":"LOCATION_NOT_VERIFIABLE","message":"Address is outside coverage"}"""),
        )

        try {
            client().post("/api/v1/x", emptyMap())
            fail("expected AddressIQError.Http")
        } catch (e: AddressIQError.Http) {
            assertEquals(422, e.status)
            assertEquals("LOCATION_NOT_VERIFIABLE", e.code)
            assertEquals("Address is outside coverage", e.msg)
        }
    }

    @Test
    fun `an empty body is not an error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        assertEquals(emptyMap<String, Any?>(), client().post("/api/v1/x/cancel", emptyMap()))
    }

    @Test
    fun `decodes a provider list`() = runTest {
        server.enqueue(
            MockResponse().setBody("""[{"type":"digital","enabled":true},{"type":"physical","enabled":false}]"""),
        )

        val providers = client().getList("/api/v1/providers?type=digital")

        assertEquals(2, providers.size)
        assertEquals(true, providers[0]["enabled"])
        assertEquals("physical", providers[1]["type"])
        assertEquals("/api/v1/providers?type=digital", server.takeRequest().path)
    }

    @Test
    fun `delete sends a body and no idempotency key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        client().delete("/api/v1/sdk/session", mapOf("appUserId" to "u1"))

        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("""{"appUserId":"u1"}""", req.body.readUtf8())
        assertNull(req.getHeader("idempotency-key"))
    }
}
