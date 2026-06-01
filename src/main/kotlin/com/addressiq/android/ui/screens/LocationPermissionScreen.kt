package com.addressiq.android.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addressiq.android.AddressIQ
import com.addressiq.android.theme.LocalAddressIQTheme
import com.addressiq.android.ui.components.AddressIQButton
import com.addressiq.android.ui.components.AddressIQButtonVariant
import com.addressiq.android.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun LocationPermissionScreen(
    onGranted: () -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalAddressIQTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var requesting by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }

    ScreenScaffold(
        scrollable = false,
        onClose = onCancel,
        footer = {
            Column {
                AddressIQButton(
                    text = "Enable Location",
                    onClick = {
                        scope.launch {
                            requesting = true
                            try {
                                val activity = context as? Activity
                                    ?: run {
                                        denied = true
                                        return@launch
                                    }
                                val result = AddressIQ.requestPermissions(activity)
                                val fg = result["foregroundLocation"]
                                if (fg == "GRANTED") onGranted() else denied = true
                            } finally {
                                requesting = false
                            }
                        }
                    },
                    loading = requesting,
                )
                if (denied) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AddressIQButton(
                        text = "Open Settings",
                        onClick = {
                            AddressIQ.openSettings(context)
                        },
                        variant = AddressIQButtonVariant.Outline,
                    )
                }
            }
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(theme.primaryLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Enable Location",
                color = theme.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "To verify your address, we need access to your location. Please keep location services turned on during the verification period (2-7 days).",
                color = theme.textSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
            )

            if (denied) {
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.errorLight, RoundedCornerShape(12.dp))
                        .border(1.dp, theme.error, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        text = "Location permission was denied. Please go to Settings and enable location access for this app.",
                        color = theme.error,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            InfoRow(icon = Icons.Filled.Lock, text = "Your location data is encrypted and secure")
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow(icon = Icons.Filled.AccessTime, text = "Location is collected periodically, not continuously")
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow(icon = Icons.Filled.Block, text = "You can opt out at any time")
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    val theme = LocalAddressIQTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(theme.primaryLight, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.size(14.dp))
        Text(text = text, color = theme.text, fontSize = 14.sp)
    }
}
