package com.addressiq.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.addressiq.android.theme.LocalAddressIQTheme
import com.addressiq.android.ui.components.AddressIQButton
import com.addressiq.android.ui.components.ScreenScaffold

/**
 * §6.6 step 6 — Street View pin-confirm. Shown as a numbered stage (2/4) only
 * when Street View metadata coverage exists at the picked point. The panorama
 * renders in a built-in WebView (no GoogleMaps SDK).
 */
@Composable
fun StreetViewScreen(
    apiKey: String,
    lat: Double,
    lon: Double,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalAddressIQTheme.current
    ScreenScaffold(
        title = "Confirm your building",
        subtitle = "Drag the view to frame your building, then confirm.",
        onClose = onCancel,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("Back", color = theme.primary) }
                AddressIQButton(text = "Confirm", onClick = onConfirm)
            }
        },
    ) {
        StreetViewWebView(apiKey = apiKey, lat = lat, lon = lon, modifier = Modifier.fillMaxWidth().height(320.dp))
    }
}
