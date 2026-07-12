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

        // There is deliberately NO default remote widget URL. The widget ships
        // as an asset (src/main/assets/iqcollect.js). If it is missing the AAR
        // is broken, and silently fetching a script from a CDN into this WebView
        // — alongside the session config — would turn a packaging bug into
        // remote code execution. Fail closed.
        //
        // `input.widgetUrl` remains an explicit developer override for serving a
        // local bundle during development.
        val widgetUrl = input.widgetUrl
        val bundledPresent = runCatching { assets.open("iqcollect.js").close() }.isSuccess
        if (!bundledPresent && widgetUrl == null) {
            finishWith(
                AddressIQVerifyResult.Failed(
                    "WIDGET_BUNDLE_MISSING",
                    "The bundled widget (assets/iqcollect.js) is missing from the AddressIQ " +
                        "SDK and no widgetUrl override was supplied. This is a packaging bug; " +
                        "the SDK will not load the widget from a remote host.",
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
