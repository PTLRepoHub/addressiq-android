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

        val apiUrl = input.deployment.defaultApiUrl()

        // Widget sourcing (see AddressIQWebFlowScreen.buildFlowHtml for the exact
        // HTML): the SRI-pinned CDN copy is the ONLY source — the SDK ships no
        // bundled widget.
        //
        //   1. `input.widgetUrl` — development-only override, wins over the CDN.
        //   2. The CDN — `{cdnUrl}/v{widgetVersion}/iqcollect.js`, loaded with
        //      `integrity="{widgetIntegrity}"`. Chromium enforces SRI, so tampered
        //      bytes never execute. A failed load reports WIDGET_LOAD_FAILED via the
        //      bridge; there is no fallback.
        //
        // With no usable pin AND no override, fail closed (a packaging bug).
        val widgetUrl = input.widgetUrl
        val cdnAvailable = cdnWidgetUrl(input.deployment) != null
        if (widgetUrl == null && !cdnAvailable) {
            finishWith(
                AddressIQVerifyResult.Failed(
                    "WIDGET_PIN_MISSING",
                    "No pinned CDN widget build is available for this deployment (empty " +
                        "version/integrity) and no widgetUrl override was supplied, so there is " +
                        "nothing safe to load. The SDK ships no bundled widget. Packaging bug.",
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
