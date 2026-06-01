package com.addressiq.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.addressiq.android.theme.LocalAddressIQTheme

enum class AddressIQButtonVariant { Primary, Secondary, Outline, Text }

@Composable
fun AddressIQButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AddressIQButtonVariant = AddressIQButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val theme = LocalAddressIQTheme.current
    val isActive = enabled && !loading
    val shape = RoundedCornerShape(theme.borderRadius)

    val content: @Composable () -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                color = if (variant == AddressIQButtonVariant.Primary) theme.buttonText else theme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.height(20.dp),
            )
        } else {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)

    when (variant) {
        AddressIQButtonVariant.Primary -> Button(
            onClick = onClick,
            enabled = isActive,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.primary,
                contentColor = theme.buttonText,
                disabledContainerColor = theme.buttonDisabledBg,
                disabledContentColor = theme.buttonText,
            ),
            modifier = rowModifier,
        ) { content() }

        AddressIQButtonVariant.Secondary -> Button(
            onClick = onClick,
            enabled = isActive,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.secondaryLight,
                contentColor = theme.buttonSecondaryText,
                disabledContainerColor = theme.buttonDisabledBg,
                disabledContentColor = theme.buttonSecondaryText,
            ),
            modifier = rowModifier,
        ) { content() }

        AddressIQButtonVariant.Outline -> OutlinedButton(
            onClick = onClick,
            enabled = isActive,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primary),
            modifier = rowModifier,
        ) { content() }

        AddressIQButtonVariant.Text -> TextButton(
            onClick = onClick,
            enabled = isActive,
            colors = ButtonDefaults.textButtonColors(contentColor = theme.textLink),
            modifier = rowModifier,
        ) { content() }
    }
}

@Composable
fun BrandingFooter() {
    val theme = LocalAddressIQTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Powered by AddressIQ",
                color = theme.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private val _unused = Color.Unspecified
private val _unusedPadding = PaddingValues(0.dp)
