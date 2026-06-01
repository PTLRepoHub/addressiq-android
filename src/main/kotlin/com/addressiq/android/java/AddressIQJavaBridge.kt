@file:JvmName("AddressIQJava")
@file:Suppress("unused")

package com.addressiq.android.java

import android.app.Activity
import android.content.Context
import com.addressiq.android.AddressIQ
import com.addressiq.android.AddressIQConfig
import com.addressiq.android.AddressIQEnvironment
import com.addressiq.android.SdkUser
import com.addressiq.android.VerificationLifecycleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

/**
 * Java-friendly façade over the Kotlin [AddressIQ] singleton.
 *
 * **Why this exists.** The Kotlin SDK uses `suspend` functions for
 * every async operation. Java callers can't invoke `suspend` directly,
 * so this façade exposes each async method as a
 * [CompletableFuture]-returning static — Java's standard async
 * primitive on API 24+. Synchronous Kotlin reads (e.g.
 * [getVerificationState]) are exposed as plain returns.
 *
 * **Usage.** All methods are static. The class name in bytecode is
 * `AddressIQJava` (via `@JvmName`) — Java callers see it as a regular
 * Java class:
 *
 * ```java
 * AddressIQJava.initialize(
 *   new AddressIQConfig("aiq_live_...", AddressIQEnvironment.PRODUCTION, null)
 * );
 *
 * AddressIQJava.setUser(new SdkUser("cust_01J9P7XK", null, null, null, null))
 *   .thenCompose(ignored -> AddressIQJava.startPhysicalVerification(
 *     "loc_abc", "dojah", null, null, null, null
 *   ))
 *   .thenAccept(result -> Log.i("AddressIQ", "Started: " + result.get("verificationCode")))
 *   .exceptionally(throwable -> {
 *     Log.e("AddressIQ", "Failed", throwable);
 *     return null;
 *   });
 * ```
 *
 * **Thread model.** All `*Future` methods dispatch on the SDK's own
 * `Dispatchers.IO`-backed scope. Callbacks chained via `thenAccept` /
 * `thenApply` / `whenComplete` run on the IO pool unless the caller
 * provides an explicit `Executor` (`thenAcceptAsync(handler, mainExec)`).
 * Marshal back to the Android main thread yourself where needed.
 */
public object AddressIQJava {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Lifecycle ──────────────────────────────────────────────────────

    /** Initialise the SDK. Synchronous — throws [IllegalArgumentException] if config is invalid. */
    @JvmStatic
    public fun initialize(config: AddressIQConfig) {
        AddressIQ.initialize(config)
    }

    /** Bind the end user. Returns a future that completes when the binding is set. */
    @JvmStatic
    public fun setUser(user: SdkUser): CompletableFuture<Void?> = scope.future {
        AddressIQ.setUser(user)
        null
    }

    @JvmStatic
    public fun pauseVerification(): CompletableFuture<Void?> = scope.future {
        AddressIQ.pauseVerification()
        null
    }

    @JvmStatic
    public fun resumeVerification(): CompletableFuture<Void?> = scope.future {
        AddressIQ.resumeVerification()
        null
    }

    /** Force-flush buffered telemetry. Returns the count of events uploaded. */
    @JvmStatic
    public fun sync(): CompletableFuture<Int> = scope.future {
        AddressIQ.sync()
    }

    @JvmStatic
    public fun logout(): CompletableFuture<Void?> = scope.future {
        AddressIQ.logout()
        null
    }

    @JvmStatic
    public fun reset(): CompletableFuture<Void?> = scope.future {
        AddressIQ.reset()
        null
    }

    /** Synchronous snapshot of the current lifecycle state. */
    @JvmStatic
    public fun getVerificationState(): VerificationLifecycleState =
        AddressIQ.getVerificationState()

    // ── Verification surface ──────────────────────────────────────────

    /**
     * Start a physical address verification. Returns a future whose
     * value is the API response (`verificationCode`, `status`, etc.).
     */
    @JvmStatic
    @JvmOverloads
    public fun startPhysicalVerification(
        locationCode: String,
        provider: String,
        agentId: String? = null,
        slaHours: Int? = null,
        idempotencyKey: String? = null,
        branchId: String? = null,
    ): CompletableFuture<Map<String, Any?>> = scope.future {
        AddressIQ.startPhysicalVerification(
            locationCode = locationCode,
            provider = provider,
            agentId = agentId,
            slaHours = slaHours,
            idempotencyKey = idempotencyKey,
            branchId = branchId,
        )
    }

    /** Start a combined digital + physical verification. */
    @JvmStatic
    @JvmOverloads
    public fun startDigitalAndPhysicalVerification(
        locationCode: String,
        physicalProvider: String,
        digitalProvider: String? = null,
        startDigital: Boolean = true,
        agentId: String? = null,
        slaHours: Int? = null,
        idempotencyKey: String? = null,
        branchId: String? = null,
    ): CompletableFuture<Map<String, Any?>> = scope.future {
        AddressIQ.startDigitalAndPhysicalVerification(
            locationCode = locationCode,
            physicalProvider = physicalProvider,
            digitalProvider = digitalProvider,
            startDigital = startDigital,
            agentId = agentId,
            slaHours = slaHours,
            idempotencyKey = idempotencyKey,
            branchId = branchId,
        )
    }

    @JvmStatic
    @JvmOverloads
    public fun cancelVerification(
        verificationCode: String,
        idempotencyKey: String? = null,
    ): CompletableFuture<Map<String, Any?>> = scope.future {
        AddressIQ.cancelVerification(verificationCode, idempotencyKey)
    }

    /** Catalog of providers the tenant can use. Pass `"physical"` or `"digital"` to filter. */
    @JvmStatic
    @JvmOverloads
    public fun listProviders(type: String? = null): CompletableFuture<List<Map<String, Any?>>> =
        scope.future {
            AddressIQ.listProviders(type)
        }

    // ── Permission orchestration (cross-SDK §0 — SDK owns the prompt) ──

    /** Synchronous read of OS permission state. See [AddressIQ.getPermissionState]. */
    @JvmStatic
    public fun getPermissionState(context: Context): Map<String, String> =
        AddressIQ.getPermissionState(context)

    /**
     * Drive the OS permission prompt sequence. Returns a future whose
     * value is the final permission state map after the prompts complete.
     * See [AddressIQ.requestPermissions] for the staging semantics.
     */
    @JvmStatic
    public fun requestPermissions(activity: Activity): CompletableFuture<Map<String, String>> =
        scope.future {
            AddressIQ.requestPermissions(activity)
        }

    @JvmStatic
    public fun shouldShowRationale(activity: Activity): Map<String, Boolean> =
        AddressIQ.shouldShowRationale(activity)

    @JvmStatic
    public fun openSettings(context: Context): Boolean =
        AddressIQ.openSettings(context)

    /**
     * Legacy-Activity fallback bridge — forward partner activity's
     * `onRequestPermissionsResult` here so the suspend chain resumes.
     */
    @JvmStatic
    public fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ): Boolean = AddressIQ.handlePermissionResult(requestCode, permissions, grantResults)

    // ── Convenience builders ──────────────────────────────────────────

    /**
     * Java-friendly fluent builder for [AddressIQConfig]. The Kotlin
     * data class uses named parameters which are awkward from Java; the
     * builder gives Java callers a clean construction path.
     *
     * ```java
     * AddressIQConfig config = AddressIQJava.config()
     *   .apiKey("aiq_live_...")
     *   .environment(AddressIQEnvironment.PRODUCTION)
     *   .build();
     * ```
     */
    @JvmStatic
    public fun config(): ConfigBuilder = ConfigBuilder()

    /**
     * Java-friendly fluent builder for [SdkUser].
     *
     * ```java
     * SdkUser user = AddressIQJava.user()
     *   .appUserId("cust_01J9P7XK")
     *   .phone("+2348012345678")
     *   .build();
     * ```
     */
    @JvmStatic
    public fun user(): UserBuilder = UserBuilder()

    public class ConfigBuilder internal constructor() {
        private var apiKey: String? = null
        private var environment: AddressIQEnvironment = AddressIQEnvironment.PRODUCTION
        private var apiUrl: String? = null

        public fun apiKey(value: String): ConfigBuilder = apply { this.apiKey = value }
        public fun environment(value: AddressIQEnvironment): ConfigBuilder = apply { this.environment = value }

        /**
         * Override the env-resolved API base URL. Production
         * integrations should leave this unset — the SDK resolves the
         * right URL from `environment`. Override only when routing
         * through a partner proxy or a hermetic test backend.
         */
        public fun apiUrl(value: String?): ConfigBuilder = apply { this.apiUrl = value }

        public fun build(): AddressIQConfig {
            val key = requireNotNull(apiKey) { "apiKey is required" }
            return AddressIQConfig(apiKey = key, environment = environment, apiUrl = apiUrl)
        }
    }

    public class UserBuilder internal constructor() {
        private var appUserId: String? = null
        private var phone: String? = null
        private var email: String? = null
        private var firstName: String? = null
        private var lastName: String? = null

        public fun appUserId(value: String): UserBuilder = apply { this.appUserId = value }
        public fun phone(value: String?): UserBuilder = apply { this.phone = value }
        public fun email(value: String?): UserBuilder = apply { this.email = value }
        public fun firstName(value: String?): UserBuilder = apply { this.firstName = value }
        public fun lastName(value: String?): UserBuilder = apply { this.lastName = value }

        public fun build(): SdkUser {
            val id = requireNotNull(appUserId) { "appUserId is required" }
            return SdkUser(appUserId = id, phone = phone, email = email, firstName = firstName, lastName = lastName)
        }
    }
}
