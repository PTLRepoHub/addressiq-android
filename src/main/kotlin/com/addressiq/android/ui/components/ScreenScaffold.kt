package com.addressiq.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addressiq.android.theme.LocalAddressIQTheme

/**
 * Common screen scaffold for the verify-flow screens. Owns the
 * header bar (back / step indicator / close), the scrollable content
 * column, and the optional footer slot for action buttons + branding.
 */
@Composable
fun ScreenScaffold(
    title: String? = null,
    subtitle: String? = null,
    step: Int? = null,
    totalSteps: Int? = null,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    scrollable: Boolean = true,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val theme = LocalAddressIQTheme.current
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background),
        color = theme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.text,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }

                if (step != null && totalSteps != null) {
                    StepIndicator(totalSteps = totalSteps, currentStep = step)
                }

                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = theme.textSecondary,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }
            }

            // Body
            val bodyModifier = if (scrollable) {
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 40.dp))
            } else {
                Modifier
                    .weight(1f)
                    .padding(20.dp)
            }
            Column(modifier = bodyModifier) {
                if (title != null) {
                    Text(
                        text = title,
                        color = theme.text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        color = theme.textSecondary,
                        fontSize = 15.sp,
                    )
                }
                if (title != null || subtitle != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                }
                content()
            }

            // Footer
            if (footer != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.background)
                        .padding(20.dp),
                ) {
                    Column {
                        footer()
                        Spacer(modifier = Modifier.height(14.dp))
                        BrandingFooter()
                    }
                }
            }
        }
    }
}
