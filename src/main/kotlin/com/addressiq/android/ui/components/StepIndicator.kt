package com.addressiq.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.addressiq.android.theme.LocalAddressIQTheme

/**
 * Pill-style step indicator. The active step's pill grows wider so
 * users can scan progress at a glance. Mirrors the React Native /
 * Flutter widgets' visual language.
 */
@Composable
fun StepIndicator(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAddressIQTheme.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        repeat(totalSteps) { index ->
            val isCompletedOrCurrent = index <= currentStep
            val isCurrent = index == currentStep
            val width by animateDpAsState(
                targetValue = if (isCurrent) 24.dp else 8.dp,
                label = "step-${index}",
            )
            Spacer(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .background(
                        color = if (isCompletedOrCurrent) theme.primary else theme.border,
                        shape = RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}
