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
import com.addressiq.android.theme.LocalAddressIQTheme
import com.addressiq.android.theme.mergeTheme
import com.addressiq.android.ui.screens.AddressIQVerifyOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow

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

        setContent {
            CompositionLocalProvider(LocalAddressIQTheme provides theme) {
                AddressIQVerifyOrchestrator(
                    input = input,
                    onCompleted = { locationCode, formattedAddress ->
                        finishWith(
                            AddressIQVerifyResult.Completed(locationCode, formattedAddress),
                        )
                    },
                    onCancelled = { finishWith(AddressIQVerifyResult.Cancelled) },
                    onFailed = { code, message ->
                        finishWith(AddressIQVerifyResult.Failed(code, message))
                    },
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
