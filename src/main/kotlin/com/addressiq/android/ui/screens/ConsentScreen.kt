package com.addressiq.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addressiq.android.theme.LocalAddressIQTheme
import com.addressiq.android.ui.AddressDraft
import com.addressiq.android.ui.components.AddressIQButton
import com.addressiq.android.ui.components.ScreenScaffold

@Composable
fun ConsentScreen(
    address: AddressDraft,
    submitting: Boolean,
    privacyPolicyUrl: String?,
    termsUrl: String?,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalAddressIQTheme.current
    val context = LocalContext.current
    var consented by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Almost done!",
        onBack = onBack,
        onClose = onCancel,
        footer = {
            AddressIQButton(
                text = "Start Verification",
                onClick = onSubmit,
                enabled = consented,
                loading = submitting,
            )
        },
    ) {
        // Summary card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                .background(theme.surface, RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Text("YOUR ADDRESS", color = theme.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (!address.formattedAddress.isNullOrBlank()) {
                Text(address.formattedAddress.orEmpty(), color = theme.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = listOfNotNull(address.propertyNumber, address.streetName).joinToString(" "),
                color = theme.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (!address.buildingColor.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    Text("Building: ", color = theme.textSecondary, fontSize = 13.sp)
                    Text(address.buildingColor.orEmpty(), color = theme.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // How it works
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF7ED), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFF9A3412), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("How it works", color = Color(0xFF9A3412), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            listOf(
                "We'll collect your location in the background for 2-7 days",
                "Keep your location services turned on",
                "Use your phone normally — no action needed",
                "You'll be notified when verification completes",
            ).forEachIndexed { idx, line ->
                if (idx > 0) Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(Color(0xFFFDBA74), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${idx + 1}", color = Color(0xFF9A3412), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(line, color = Color(0xFF78350F), fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Consent checkbox
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { consented = !consented }
                .padding(vertical = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (consented) theme.primary else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .border(2.dp, if (consented) theme.primary else theme.border, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (consented) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                buildString {
                    append("I agree to background location collection for address verification")
                    if (termsUrl != null || privacyPolicyUrl != null) append(" and accept the linked policies")
                    append(".")
                },
                color = theme.text,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }

        if (termsUrl != null || privacyPolicyUrl != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (termsUrl != null) {
                    PolicyLink("Terms & Conditions", termsUrl, modifier = Modifier.weight(1f)) { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                if (privacyPolicyUrl != null) {
                    PolicyLink("Privacy Policy", privacyPolicyUrl, modifier = Modifier.weight(1f)) { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyLink(text: String, url: String, modifier: Modifier, onClick: (String) -> Unit) {
    val theme = LocalAddressIQTheme.current
    Box(
        modifier = modifier
            .border(1.dp, theme.border, RoundedCornerShape(10.dp))
            .clickable { onClick(url) }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = theme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
