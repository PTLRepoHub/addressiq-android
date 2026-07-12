package com.addressiq.android.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.addressiq.android.theme.LocalAddressIQTheme
import com.addressiq.android.theme.mergeTheme
import com.addressiq.android.ui.screens.AddressIQWebFlow
import com.addressiq.android.ui.screens.cdnWidgetUrl

/**
 * Drop-in verify activity. Launched via [AddressIQVerifyContract] —
 * partners never `startActivity()` this class directly; the contract
 * handles intent construction + result parsing.
 *
 * The activity owns the SDK-themed widget tree and forwards lifecycle
 * events back to the partner through the `ActivityResultContract`
 * registered in [AddressIQVerifyContract.parseResult].
 */
class AddressIQVerifyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = intent.getParcelableExtra<AddressIQVerifyInput>(EXTRA_INPUT)
            ?: run {
                finishWith(AddressIQVerifyResult.Failed("INVALID_CONFIG", "AddressIQVerifyInput missing"))
                return
            }

        val theme = mergeTheme(input.theme)

        val apiUrl = input.environment.defaultApiUrl()

        // Widget sourcing (see AddressIQWebFlowScreen.buildFlowHtml for the exact
        // HTML): CDN-first, SRI-pinned, bundled fallback.
        //
        //   1. `input.widgetUrl` — explicit developer override, wins over everything.
        //   2. The CDN — `{cdnUrl}/v{widgetVersion}/iqcollect.js`, loaded with
        //      `integrity="{widgetIntegrity}"`. The hash is baked from
        //      `.widget-integrity`, written by addressiq-web's fanout PR from the
        //      same build as the vendored asset, and the CDN's /v{x.y.z}/ paths are
        //      immutable — so the pin is meaningful. Chromium (this WebView) enforces
        //      SRI, so a tampered bundle refuses to execute and trips onerror rather
        //      than running attacker JS next to the session config.
        //   3. The bundled asset (src/main/assets/iqcollect.js), injected by the
        //      onerror fallback — covers CDN outage, offline devices, and SRI failure.
        //      It is also the ONLY source when the CDN preconditions are not met
        //      (DEVELOPMENT, or version/integrity/cdn not baked).
        //
        // With no bundled asset AND no usable remote source, fail closed.
        val widgetUrl = input.widgetUrl
        val bundledPresent = runCatching { assets.open("iqcollect.js").close() }.isSuccess
        val cdnAvailable = cdnWidgetUrl(input.environment) != null
        if (!bundledPresent && widgetUrl == null && !cdnAvailable) {
            finishWith(
                AddressIQVerifyResult.Failed(
                    "WIDGET_BUNDLE_MISSING",
                    "The bundled widget (assets/iqcollect.js) is missing from the AddressIQ " +
                        "SDK, no pinned CDN build is available for this environment, and no " +
                        "widgetUrl override was supplied. This is a packaging bug.",
                ),
            )
            return
        }

        setContent {
            CompositionLocalProvider(LocalAddressIQTheme provides theme) {
                // The UI is now the shared web widget hosted in a WebView. This
                // Activity owns only the native pieces: precise/Always permission
                // and the location fix, bridged to the widget.
                AddressIQWebFlow(
                    input = input,
                    apiUrl = apiUrl,
                    widgetUrl = widgetUrl,
                    onCompleted = { locationCode, formattedAddress ->
                        finishWith(
                            AddressIQVerifyResult.Completed(locationCode, formattedAddress),
                        )
                    },
                    onCancelled = { finishWith(AddressIQVerifyResult.Cancelled) },
                    onFailed = { code, message ->
                        finishWith(AddressIQVerifyResult.Failed(code, message))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun finishWith(result: AddressIQVerifyResult) {
        setResult(
            Activity.RESULT_OK,
            Intent().apply { putExtra(EXTRA_RESULT, result) },
        )
        finish()
    }

    companion object {
        internal const val EXTRA_INPUT = "addressiq.verify.input"
        internal const val EXTRA_RESULT = "addressiq.verify.result"
    }
}
