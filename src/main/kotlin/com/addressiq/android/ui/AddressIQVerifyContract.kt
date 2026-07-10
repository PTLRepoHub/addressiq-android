package com.addressiq.android.ui

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import com.addressiq.android.AddressIQEnvironment
import com.addressiq.android.theme.AddressIQThemeOverrides
import kotlinx.parcelize.Parcelize

/**
 * Public launch contract for [AddressIQVerifyActivity]. Partners
 * register the contract via `ActivityResultContracts` and get a typed
 * [AddressIQVerifyResult] callback:
 *
 *   ```kotlin
 *   class MyActivity : AppCompatActivity() {
 *     private val verify = registerForActivityResult(AddressIQVerifyContract()) { result ->
 *       when (result) {
 *         is AddressIQVerifyResult.Completed -> startVerification(result.locationCode)
 *         is AddressIQVerifyResult.Cancelled -> { }
 *         is AddressIQVerifyResult.Failed    -> showError(result.code, result.message)
 *       }
 *     }
 *
 *     fun startVerifyFlow() {
 *       verify.launch(
 *         AddressIQVerifyInput(
 *           apiKey = "aiq_live_...",
 *           appUserId = customer.id,
 *           environment = AddressIQEnvironment.PRODUCTION,
 *         ),
 *       )
 *     }
 *   }
 *   ```
 */
class AddressIQVerifyContract : ActivityResultContract<AddressIQVerifyInput, AddressIQVerifyResult>() {

    override fun createIntent(context: Context, input: AddressIQVerifyInput): Intent {
        return Intent(context, AddressIQVerifyActivity::class.java).apply {
            putExtra(AddressIQVerifyActivity.EXTRA_INPUT, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): AddressIQVerifyResult {
        return intent?.getParcelableExtra(AddressIQVerifyActivity.EXTRA_RESULT)
            ?: AddressIQVerifyResult.Cancelled
    }
}

/**
 * Input to the verify activity. `appUserId` is the primary identifier
 * the AddressIQ backend uses for dedup, push routing, and session
 * lookup — pass your stable customer ID. The other fields are optional
 * contact information / pre-fills.
 */
@Parcelize
data class AddressIQVerifyInput(
    val apiKey: String,
    val appUserId: String,
    val environment: AddressIQEnvironment = AddressIQEnvironment.PRODUCTION,
    val phone: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    /** Google Maps API key — enables the address map flow (autocomplete, pin,
     *  reverse geocode, Street View). When null the step degrades to manual entry. */
    val googleMapsApiKey: String? = null,
    val theme: AddressIQThemeOverrides? = null,
    val privacyPolicyUrl: String? = null,
    val termsUrl: String? = null,
    /** Business display name shown on the intro/consent screens. */
    val businessName: String? = null,
    /** Override the hosted widget bundle URL (for local development). */
    val widgetUrl: String? = null,
    /** Override the resolved API base URL (for local development / mock upstream). */
    val apiUrlOverride: String? = null,
) : Parcelable

/** Sealed result type returned to the partner via [AddressIQVerifyContract]. */
sealed class AddressIQVerifyResult : Parcelable {
    /**
     * Address collected successfully. The Collect UI is **collect-only** — it
     * saves the address and returns its [locationCode]; it does NOT start a
     * verification. Start verification from the result with
     * `AddressIQ.startVerification(context, locationCode, …)`.
     */
    @Parcelize
    data class Completed(
        val locationCode: String,
        val formattedAddress: String?,
        val isExisting: Boolean = false,
    ) : AddressIQVerifyResult()

    /** User dismissed the flow (back button, explicit close). */
    @Parcelize
    object Cancelled : AddressIQVerifyResult()

    /** Flow surfaced a typed error before the user could finish. */
    @Parcelize
    data class Failed(val code: String, val message: String) : AddressIQVerifyResult()
}
