package com.addressiq.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addressiq.android.theme.LocalAddressIQTheme
import com.addressiq.android.ui.AddressDraft
import com.addressiq.android.ui.components.AddressIQButton
import com.addressiq.android.ui.components.ScreenScaffold

@Composable
fun AddressScreen(
    initial: AddressDraft,
    onNext: (AddressDraft) -> Unit,
    onCancel: () -> Unit,
    fetchLocation: suspend () -> Pair<Double, Double>?,
) {
    val theme = LocalAddressIQTheme.current
    val scope = rememberCoroutineScope()

    var lat by remember { mutableStateOf(initial.lat) }
    var lon by remember { mutableStateOf(initial.lon) }
    var accuracy by remember { mutableStateOf(0.0) }
    var formatted by remember { mutableStateOf(initial.formattedAddress.orEmpty()) }
    var loading by remember { mutableStateOf(false) }

    suspend fun capture() {
        loading = true
        try {
            val reading = fetchLocation()
            if (reading != null) {
                lat = reading.first
                lon = reading.second
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (lat == null || lon == null) capture()
    }

    val canContinue = lat != null && lon != null && formatted.trim().isNotEmpty()

    ScreenScaffold(
        title = "Confirm your address",
        subtitle = "We'll use your current location and the address you enter to verify where you live.",
        step = 0,
        totalSteps = 3,
        onClose = onCancel,
        footer = {
            AddressIQButton(
                text = "Continue",
                onClick = {
                    onNext(
                        initial.copy(
                            lat = lat,
                            lon = lon,
                            formattedAddress = formatted.trim(),
                        ),
                    )
                },
                enabled = canContinue,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface, RoundedCornerShape(theme.borderRadiusLg))
                .border(1.dp, theme.border, RoundedCornerShape(theme.borderRadiusLg))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "CURRENT LOCATION",
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(10.dp))
                    Text("Reading GPS…", color = theme.textSecondary, fontSize = 13.sp)
                }
            } else if (lat != null && lon != null) {
                Text(
                    text = "%.6f, %.6f".format(lat, lon),
                    color = theme.text,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                if (accuracy > 0) {
                    Text(
                        text = "±${accuracy.toInt()} m accuracy",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                TextButton(onClick = { scope.launch { capture() } }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                        Text("Read again", color = theme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text("Formatted address", color = theme.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = formatted,
            onValueChange = { formatted = it },
            placeholder = { Text("e.g. 1 Marina, Lagos Island, Lagos", color = theme.inputPlaceholder) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().height(80.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.inputText,
                unfocusedTextColor = theme.inputText,
                focusedBorderColor = theme.borderFocused,
                unfocusedBorderColor = theme.inputBorder,
                cursorColor = theme.primary,
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter your address exactly as it should appear on official records. The next screen captures property details.",
            color = theme.textSecondary,
            fontSize = 12.sp,
        )
    }
}
