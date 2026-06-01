@file:Suppress("unused")

package com.addressiq.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class AddressIQEnvironment {
    SANDBOX,
    PRODUCTION;

    /** Public API base URL the SDK resolves to when no override is set. */
    public fun defaultApiUrl(): String = when (this) {
        PRODUCTION -> "https://api.addressiqpro.com"
        SANDBOX -> "https://api-staging.addressiqpro.com"
    }
}

@Serializable
data class AddressIQConfig(
    val apiKey: String,
    val environment: AddressIQEnvironment = AddressIQEnvironment.PRODUCTION,
    /**
     * Optional override for the API base URL. Production integrations
     * should leave this null — the SDK resolves the right URL from
     * [environment]. Override only when routing through a partner
     * proxy or running against a hermetic test backend.
     */
    val apiUrl: String? = null,
) {
    /** Effective API URL: explicit override if set, otherwise the env default. */
    val resolvedApiUrl: String get() = apiUrl ?: environment.defaultApiUrl()
}

@Serializable
data class SdkUser(
    val appUserId: String,
    val phone: String? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)

enum class AddressIQLifecycleState { UNINITIALIZED, IDLE, COLLECTING, PAUSED, TERMINATED }

@Serializable
data class VerificationLifecycleState(
    val state: AddressIQLifecycleState,
    val appUserId: String? = null,
    val verificationId: String? = null,
    val locationCode: String? = null,
    val pausedForMs: Long? = null,
)

sealed class AddressIQError(message: String) : Exception(message) {
    object NotInitialized : AddressIQError("AddressIQ.initialize must be called first")
    object NoActiveSession : AddressIQError("No active verification session")
    data class Http(val status: Int, val code: String?, val msg: String?) :
        AddressIQError("AddressIQ HTTP $status (${code ?: "?"}): ${msg ?: ""}")
}

/**
 * Public singleton — `AddressIQ.initialize(config)`, `AddressIQ.setUser(user)`, etc.
 *
 * Mirrors the RN + Flutter + iOS SDKs at the method level. All async methods
 * are `suspend`; status streams are exposed via `StateFlow<VerificationLifecycleState>`.
 *
 * Internal architecture: Repository pattern (this object is the public face;
 * the per-domain `AddressIQRepository` is package-private and abstracts the
 * generated OkHttp client + SQLCipher telemetry queue). No Koin/Hilt mandate
 * — partners wire dependencies via the public `AddressIQ.configure(...)` DSL.
 */
object AddressIQ {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile private var config: AddressIQConfig? = null
    @Volatile private var currentUser: SdkUser? = null
    @Volatile private var activeVerificationId: String? = null
    @Volatile private var activeLocationCode: String? = null
    @Volatile private var pausedAtMs: Long? = null
    @Volatile private var state: AddressIQLifecycleState = AddressIQLifecycleState.UNINITIALIZED

    private val _stateFlow = MutableStateFlow(getVerificationStateSnapshot())
    val stateFlow: StateFlow<VerificationLifecycleState> = _stateFlow.asStateFlow()

    // ── Lifecycle ──────────────────────────────────────────────────────────

    fun initialize(config: AddressIQConfig) {
        require(config.apiKey.isNotEmpty()) { "apiKey is required" }
        require(config.resolvedApiUrl.isNotEmpty()) { "apiUrl resolved to empty string (check environment)" }
        this.config = config
        state = AddressIQLifecycleState.IDLE
        emitStateChange()
    }

    suspend fun setUser(user: SdkUser) {
        requireInitialized()
        require(user.appUserId.isNotEmpty()) { "appUserId is required" }
        if (currentUser != null && currentUser!!.appUserId != user.appUserId) {
            pauseVerification()
        }
        currentUser = user
        if (state == AddressIQLifecycleState.UNINITIALIZED ||
            state == AddressIQLifecycleState.TERMINATED
        ) {
            state = AddressIQLifecycleState.IDLE
        }
        emitStateChange()
    }

    suspend fun pauseVerification() {
        if (state != AddressIQLifecycleState.COLLECTING) return
        pausedAtMs = System.currentTimeMillis()
        state = AddressIQLifecycleState.PAUSED
        emitStateChange()
    }

    suspend fun resumeVerification() {
        if (state != AddressIQLifecycleState.PAUSED) return
        if (activeVerificationId == null || activeLocationCode == null) {
            throw AddressIQError.NoActiveSession
        }
        pausedAtMs = null
        state = AddressIQLifecycleState.COLLECTING
        emitStateChange()
    }

    suspend fun sync(): Int {
        // Telemetry queue flush — wires up alongside the protobuf-aligned
        // envelope migration (P3.7). Stub returns 0 today.
        return 0
    }

    suspend fun logout() {
        val cfg = config ?: return
        val user = currentUser
        pauseVerification()
        if (user != null) {
            runCatching {
                deleteSession(cfg, user.appUserId, activeVerificationId)
            }
        }
        currentUser = null
        activeVerificationId = null
        activeLocationCode = null
        pausedAtMs = null
        state = AddressIQLifecycleState.TERMINATED
        emitStateChange()
    }

    suspend fun reset() {
        currentUser = null
        activeVerificationId = null
        activeLocationCode = null
        pausedAtMs = null
        state = AddressIQLifecycleState.UNINITIALIZED
        config = null
        emitStateChange()
    }

    fun getVerificationState(): VerificationLifecycleState = getVerificationStateSnapshot()

    // ─── Permission orchestration ────────────────────────────────────────
    //
    // Cross-SDK contract §0 (Permission Trigger Ownership): the
    // integrating app decides *when* verification begins; the SDK owns
    // every step after that. The four methods below are how that
    // ownership surfaces on Android. Implementation lives in
    // [PermissionRequester] so this singleton stays focused on lifecycle.

    /**
     * Read-only permission snapshot. Use to decide whether to render a
     * rationale screen *before* calling [requestPermissions]. The SDK
     * never mutates state from this call.
     *
     * Returned map keys: `foregroundLocation`, `backgroundLocation`,
     * `notifications`. Values drawn from `{GRANTED, DENIED,
     * NOT_DETERMINED, BLOCKED, UNAVAILABLE}`.
     *
     * Pass an `Activity` (or any subclass — `AppCompatActivity`,
     * `FragmentActivity`, `ComponentActivity`) to get the precise
     * `BLOCKED` vs `NOT_DETERMINED` tri-state. With an `Application`
     * or `Service` context, both collapse to `NOT_DETERMINED` because
     * the `shouldShowRequestPermissionRationale` check requires an
     * `Activity` reference.
     */
    fun getPermissionState(context: android.content.Context): Map<String, String> =
        com.addressiq.android.permissions.PermissionRequester.getPermissionState(context)

    /**
     * Drive the OS permission prompts and return the final state.
     *
     * Three-stage sequencing (foreground location → notifications →
     * background location) matches Google policy: Android 11+ silently
     * no-ops combined foreground+background requests and force-redirects
     * to Settings, so the SDK splits them so the prompt actually
     * appears.
     *
     * Modern hosts (`ComponentActivity` and subclasses) use
     * `ActivityResultContracts.RequestMultiplePermissions` — no
     * pre-registration step required, no manual result forwarding.
     * Plain-`Activity` hosts use `ActivityCompat.requestPermissions`
     * + forward their `onRequestPermissionsResult` to
     * [handlePermissionResult] so this suspend can resume.
     *
     * @return final state map with the same shape as [getPermissionState].
     */
    suspend fun requestPermissions(activity: android.app.Activity): Map<String, String> =
        com.addressiq.android.permissions.PermissionRequester.requestPermissions(activity)

    /**
     * Per-permission rationale flag. Returns `true` for a permission
     * when the OS allows showing a rationale (i.e. the user denied once
     * but didn't tick "Don't ask again"). Use this to gate "Why we
     * need this" UI between requests.
     */
    fun shouldShowRationale(activity: android.app.Activity): Map<String, Boolean> =
        com.addressiq.android.permissions.PermissionRequester.shouldShowRationale(activity)

    /**
     * Deep-link to the host app's settings page so the user can
     * re-enable a permanently-denied permission. Returns `true` if the
     * intent resolved (always does on real devices; only false on a
     * malformed test harness).
     */
    fun openSettings(context: android.content.Context): Boolean =
        com.addressiq.android.permissions.PermissionRequester.openSettings(context)

    /**
     * Legacy-Activity fallback bridge. Partners hosting AddressIQ from
     * a plain `Activity` (no AndroidX) must forward their
     * `onRequestPermissionsResult` override here so the SDK's pending
     * suspend can resume:
     *
     * ```kotlin
     * override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
     *     super.onRequestPermissionsResult(rc, p, g)
     *     AddressIQ.handlePermissionResult(rc, p, g)
     * }
     * ```
     *
     * No-op when the request code wasn't issued by the SDK — safe to
     * forward unconditionally. Returns `true` when the call was handled.
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ): Boolean = com.addressiq.android.permissions.PermissionRequester.handlePermissionResult(
        requestCode, permissions, grantResults,
    )

    private fun getVerificationStateSnapshot() = VerificationLifecycleState(
        state = state,
        appUserId = currentUser?.appUserId,
        verificationId = activeVerificationId,
        locationCode = activeLocationCode,
        pausedForMs = pausedAtMs?.let { System.currentTimeMillis() - it },
    )

    // ── Verification surface ──────────────────────────────────────────────

    /**
     * Start a physical address verification. A partner-provided agent
     * or KYC provider visits the address to confirm residency.
     */
    suspend fun startPhysicalVerification(
        locationCode: String,
        provider: String,
        agentId: String? = null,
        slaHours: Int? = null,
        idempotencyKey: String? = null,
        branchId: String? = null,
    ): Map<String, Any?> {
        val cfg = requireInitialized()
        val url = "${cfg.resolvedApiUrl}/api/v1/locations/$locationCode/verifications/physical"
        val body = buildMap {
            put("provider", provider)
            agentId?.let { put("agentId", it) }
            slaHours?.let { put("slaHours", it) }
        }
        return post(cfg, url, body, idempotencyKey, branchId)
    }

    /**
     * Start a combined digital + physical verification. Digital runs
     * first via the AI provider (uses SDK telemetry to score residency);
     * physical fallback fires if the digital half resolves to UNKNOWN.
     */
    suspend fun startDigitalAndPhysicalVerification(
        locationCode: String,
        physicalProvider: String,
        digitalProvider: String? = null,
        startDigital: Boolean = true,
        agentId: String? = null,
        slaHours: Int? = null,
        idempotencyKey: String? = null,
        branchId: String? = null,
    ): Map<String, Any?> {
        val cfg = requireInitialized()
        val url = "${cfg.resolvedApiUrl}/api/v1/locations/$locationCode/verifications/combined"
        val body = buildMap<String, Any?> {
            put("physicalProvider", physicalProvider)
            put("startDigital", startDigital)
            digitalProvider?.let { put("digitalProvider", it) }
            agentId?.let { put("agentId", it) }
            slaHours?.let { put("slaHours", it) }
        }
        return post(cfg, url, body, idempotencyKey, branchId)
    }


    suspend fun cancelVerification(
        verificationCode: String,
        idempotencyKey: String? = null,
    ): Map<String, Any?> {
        val cfg = requireInitialized()
        val url = "${cfg.resolvedApiUrl}/api/v1/verifications/$verificationCode/cancel"
        return post(cfg, url, emptyMap(), idempotencyKey, null)
    }

    suspend fun listProviders(type: String? = null): List<Map<String, Any?>> {
        val cfg = requireInitialized()
        val url = "${cfg.resolvedApiUrl}/api/v1/providers" + (type?.let { "?type=$it" } ?: "")
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("x-api-key", cfg.apiKey)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw AddressIQError.Http(resp.code, null, resp.message)
                @Suppress("UNCHECKED_CAST")
                json.decodeFromString<List<Map<String, String?>>>(resp.body?.string().orEmpty()) as List<Map<String, Any?>>
            }
        }
    }

    /** Internal — startVerification paths use this to mark COLLECTING. */
    fun markActiveSession(locationCode: String, verificationId: String) {
        activeLocationCode = locationCode
        activeVerificationId = verificationId
        state = AddressIQLifecycleState.COLLECTING
        pausedAtMs = null
        emitStateChange()
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private fun requireInitialized(): AddressIQConfig =
        config ?: throw AddressIQError.NotInitialized

    private fun emitStateChange() {
        _stateFlow.value = getVerificationStateSnapshot()
    }

    private suspend fun post(
        cfg: AddressIQConfig,
        url: String,
        body: Map<String, Any?>,
        idempotencyKey: String?,
        branchId: String?,
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(JsonAny.toJson(body))
        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("x-api-key", cfg.apiKey)
            .header("idempotency-key", idempotencyKey ?: makeIdempotencyKey())
            .apply { branchId?.let { header("x-branch-id", it) } }
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            @Suppress("UNCHECKED_CAST")
            val parsed = if (raw.isBlank()) emptyMap<String, Any?>()
            else json.decodeFromString<Map<String, String?>>(raw) as Map<String, Any?>
            if (!resp.isSuccessful) {
                throw AddressIQError.Http(
                    resp.code,
                    parsed["code"] as? String,
                    parsed["message"] as? String ?: resp.message,
                )
            }
            parsed
        }
    }

    private suspend fun deleteSession(
        cfg: AddressIQConfig,
        appUserId: String,
        verificationCode: String?,
    ) = withContext(Dispatchers.IO) {
        val body = buildMap<String, Any?> {
            put("appUserId", appUserId)
            verificationCode?.let { put("verificationCode", it) }
        }
        val req = Request.Builder()
            .url("${cfg.resolvedApiUrl}/api/v1/sdk/session")
            .delete(json.encodeToString(JsonAny.toJson(body)).toRequestBody("application/json".toMediaType()))
            .header("x-api-key", cfg.apiKey)
            .build()
        http.newCall(req).execute().close()
    }

    private fun makeIdempotencyKey(): String =
        "iqidem_android_${UUID.randomUUID().toString().replace("-", "").take(16)}"
}

/** Tiny adapter — kotlinx.serialization needs typed inputs; this lets us pass `Map<String,Any?>`. */
private object JsonAny {
    @Serializable
    data class StringMap(val data: Map<String, String?> = emptyMap())

    fun toJson(value: Map<String, Any?>): Map<String, String?> =
        value.mapValues { (_, v) -> v?.toString() }
}
