package com.addressiq.android.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addressiq.android.theme.LocalAddressIQTheme
import com.addressiq.android.ui.components.AddressIQButton
import com.addressiq.android.ui.components.BrandingFooter

@Composable
fun SuccessScreen(
    verificationId: String,
    onDone: () -> Unit,
) {
    val theme = LocalAddressIQTheme.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val checkScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "check",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "text",
    )

    Column(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(checkScale)
                    .background(theme.success, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(modifier = Modifier.height(28.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Verification Started",
                    color = theme.text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.alpha(textAlpha),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "We're now verifying your address. This typically takes 2-7 days. You'll be notified when it's complete.",
                    color = theme.textSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 10.dp).alpha(textAlpha),
                )
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.primaryLight, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                        .alpha(textAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        "Keep your location services turned on and don't force-close this app for the best results",
                        color = theme.text,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    "Reference: $verificationId",
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.alpha(textAlpha),
                )
            }
        }
        Column(modifier = Modifier.padding(20.dp)) {
            AddressIQButton(text = "Done", onClick = onDone)
            Spacer(modifier = Modifier.height(12.dp))
            BrandingFooter()
        }
    }
}
