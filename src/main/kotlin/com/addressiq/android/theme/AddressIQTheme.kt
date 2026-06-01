package com.addressiq.android.theme

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith

/**
 * Cross-SDK canonical theme. Same 32-token surface as the React Native
 * and Flutter widgets — partners can take a theme defined for one SDK
 * and feed it into the others via field-by-field translation.
 *
 * Partners pass a `Partial<AddressIQTheme>` (any subset of tokens) via
 * [AddressIQVerifyInput]; [mergeTheme] fills missing fields from
 * [DEFAULT_THEME] and auto-derives related shades from any provided
 * `primary` so a single brand color produces a coherent palette.
 *
 * Authoritative shape: `docs/sdk-contract.md` §1.5.
 */
@Immutable
data class AddressIQTheme(
    // Brand
    val primary: Color = Color(0xFF4F46E5),
    val primaryDark: Color = Color(0xFF4338CA),
    val primaryLight: Color = Color(0xFFEEF2FF),
    val secondary: Color = Color(0xFF6B7280),
    val secondaryDark: Color = Color(0xFF4B5563),
    val secondaryLight: Color = Color(0xFFF3F4F6),
    val accent: Color = Color(0xFF8B5CF6),

    // Backgrounds
    val background: Color = Color(0xFFF9FAFB),
    val surface: Color = Color(0xFFFFFFFF),
    val surfaceSecondary: Color = Color(0xFFF3F4F6),
    val modalOverlay: Color = Color(0x80000000),

    // Text
    val text: Color = Color(0xFF1F2937),
    val textSecondary: Color = Color(0xFF6B7280),
    val textInverse: Color = Color(0xFFFFFFFF),
    val textLink: Color = Color(0xFF4F46E5),

    // Borders
    val border: Color = Color(0xFFE5E7EB),
    val borderFocused: Color = Color(0xFF4F46E5),
    val divider: Color = Color(0xFFF3F4F6),

    // Status
    val error: Color = Color(0xFFDC2626),
    val errorLight: Color = Color(0xFFFEF2F2),
    val success: Color = Color(0xFF16A34A),
    val successLight: Color = Color(0xFFF0FDF4),
    val warning: Color = Color(0xFFF59E0B),
    val warningLight: Color = Color(0xFFFFFBEB),
    val info: Color = Color(0xFF3B82F6),
    val infoLight: Color = Color(0xFFEFF6FF),

    // Buttons
    val buttonText: Color = Color(0xFFFFFFFF),
    val buttonSecondaryText: Color = Color(0xFF374151),
    val buttonDisabledBg: Color = Color(0xFFD1D5DB),

    // Input
    val inputBg: Color = Color(0xFFFFFFFF),
    val inputBorder: Color = Color(0xFFD1D5DB),
    val inputText: Color = Color(0xFF1F2937),
    val inputPlaceholder: Color = Color(0xFF9CA3AF),

    // Card
    val cardBg: Color = Color(0xFFFFFFFF),
    val cardBorder: Color = Color(0xFFE5E7EB),

    // Typography
    val fontFamily: FontFamily = FontFamily.Default,
    val fontFamilyMono: FontFamily = FontFamily.Monospace,

    // Radius
    val borderRadius: Dp = 12.dp,
    val borderRadiusLg: Dp = 16.dp,
    val borderRadiusSm: Dp = 8.dp,
)

/** Default palette — partners typically override `primary` only. */
val DEFAULT_THEME = AddressIQTheme()

/**
 * `CompositionLocal` carrying the active theme through the verify-flow
 * widget tree. Screens read it via `LocalAddressIQTheme.current`.
 */
val LocalAddressIQTheme = compositionLocalOf { DEFAULT_THEME }

/**
 * Merge a partial token override on top of [DEFAULT_THEME]. When the
 * partner provides only `primary`, this auto-derives `primaryDark`,
 * `primaryLight`, `borderFocused`, and `textLink` so a single brand
 * color yields a coherent palette (matches the RN SDK's behavior).
 */
fun mergeTheme(overrides: AddressIQThemeOverrides?): AddressIQTheme {
    if (overrides == null) return DEFAULT_THEME

    val primary = overrides.primary
    val derived = if (primary != null) {
        DEFAULT_THEME.copy(
            primary = primary,
            primaryDark = overrides.primaryDark ?: darken(primary, 0.15f),
            primaryLight = overrides.primaryLight ?: lighten(primary, 0.90f),
            borderFocused = overrides.borderFocused ?: primary,
            textLink = overrides.textLink ?: primary,
        )
    } else {
        DEFAULT_THEME
    }

    return derived.copy(
        primaryDark = overrides.primaryDark ?: derived.primaryDark,
        primaryLight = overrides.primaryLight ?: derived.primaryLight,
        secondary = overrides.secondary ?: derived.secondary,
        secondaryDark = overrides.secondaryDark ?: derived.secondaryDark,
        secondaryLight = overrides.secondaryLight ?: derived.secondaryLight,
        accent = overrides.accent ?: derived.accent,
        background = overrides.background ?: derived.background,
        surface = overrides.surface ?: derived.surface,
        surfaceSecondary = overrides.surfaceSecondary ?: derived.surfaceSecondary,
        modalOverlay = overrides.modalOverlay ?: derived.modalOverlay,
        text = overrides.text ?: derived.text,
        textSecondary = overrides.textSecondary ?: derived.textSecondary,
        textInverse = overrides.textInverse ?: derived.textInverse,
        textLink = overrides.textLink ?: derived.textLink,
        border = overrides.border ?: derived.border,
        borderFocused = overrides.borderFocused ?: derived.borderFocused,
        divider = overrides.divider ?: derived.divider,
        error = overrides.error ?: derived.error,
        errorLight = overrides.errorLight ?: derived.errorLight,
        success = overrides.success ?: derived.success,
        successLight = overrides.successLight ?: derived.successLight,
        warning = overrides.warning ?: derived.warning,
        warningLight = overrides.warningLight ?: derived.warningLight,
        info = overrides.info ?: derived.info,
        infoLight = overrides.infoLight ?: derived.infoLight,
        buttonText = overrides.buttonText ?: derived.buttonText,
        buttonSecondaryText = overrides.buttonSecondaryText ?: derived.buttonSecondaryText,
        buttonDisabledBg = overrides.buttonDisabledBg ?: derived.buttonDisabledBg,
        inputBg = overrides.inputBg ?: derived.inputBg,
        inputBorder = overrides.inputBorder ?: derived.inputBorder,
        inputText = overrides.inputText ?: derived.inputText,
        inputPlaceholder = overrides.inputPlaceholder ?: derived.inputPlaceholder,
        cardBg = overrides.cardBg ?: derived.cardBg,
        cardBorder = overrides.cardBorder ?: derived.cardBorder,
        fontFamily = overrides.fontFamily ?: derived.fontFamily,
        fontFamilyMono = overrides.fontFamilyMono ?: derived.fontFamilyMono,
        borderRadius = overrides.borderRadius ?: derived.borderRadius,
        borderRadiusLg = overrides.borderRadiusLg ?: derived.borderRadiusLg,
        borderRadiusSm = overrides.borderRadiusSm ?: derived.borderRadiusSm,
    )
}

/**
 * Partial token surface partners hand in. All-nullable so partners only
 * override what they care about; [mergeTheme] fills the rest.
 */
@Immutable
@Parcelize
data class AddressIQThemeOverrides(
    val primary: @WriteWith<ColorParceler> Color? = null,
    val primaryDark: @WriteWith<ColorParceler> Color? = null,
    val primaryLight: @WriteWith<ColorParceler> Color? = null,
    val secondary: @WriteWith<ColorParceler> Color? = null,
    val secondaryDark: @WriteWith<ColorParceler> Color? = null,
    val secondaryLight: @WriteWith<ColorParceler> Color? = null,
    val accent: @WriteWith<ColorParceler> Color? = null,
    val background: @WriteWith<ColorParceler> Color? = null,
    val surface: @WriteWith<ColorParceler> Color? = null,
    val surfaceSecondary: @WriteWith<ColorParceler> Color? = null,
    val modalOverlay: @WriteWith<ColorParceler> Color? = null,
    val text: @WriteWith<ColorParceler> Color? = null,
    val textSecondary: @WriteWith<ColorParceler> Color? = null,
    val textInverse: @WriteWith<ColorParceler> Color? = null,
    val textLink: @WriteWith<ColorParceler> Color? = null,
    val border: @WriteWith<ColorParceler> Color? = null,
    val borderFocused: @WriteWith<ColorParceler> Color? = null,
    val divider: @WriteWith<ColorParceler> Color? = null,
    val error: @WriteWith<ColorParceler> Color? = null,
    val errorLight: @WriteWith<ColorParceler> Color? = null,
    val success: @WriteWith<ColorParceler> Color? = null,
    val successLight: @WriteWith<ColorParceler> Color? = null,
    val warning: @WriteWith<ColorParceler> Color? = null,
    val warningLight: @WriteWith<ColorParceler> Color? = null,
    val info: @WriteWith<ColorParceler> Color? = null,
    val infoLight: @WriteWith<ColorParceler> Color? = null,
    val buttonText: @WriteWith<ColorParceler> Color? = null,
    val buttonSecondaryText: @WriteWith<ColorParceler> Color? = null,
    val buttonDisabledBg: @WriteWith<ColorParceler> Color? = null,
    val inputBg: @WriteWith<ColorParceler> Color? = null,
    val inputBorder: @WriteWith<ColorParceler> Color? = null,
    val inputText: @WriteWith<ColorParceler> Color? = null,
    val inputPlaceholder: @WriteWith<ColorParceler> Color? = null,
    val cardBg: @WriteWith<ColorParceler> Color? = null,
    val cardBorder: @WriteWith<ColorParceler> Color? = null,
    val fontFamily: @WriteWith<FontFamilyParceler> FontFamily? = null,
    val fontFamilyMono: @WriteWith<FontFamilyParceler> FontFamily? = null,
    val borderRadius: @WriteWith<DpParceler> Dp? = null,
    val borderRadiusLg: @WriteWith<DpParceler> Dp? = null,
    val borderRadiusSm: @WriteWith<DpParceler> Dp? = null,
) : Parcelable

private fun darken(color: Color, fraction: Float): Color {
    val f = 1f - fraction.coerceIn(0f, 1f)
    return Color(red = color.red * f, green = color.green * f, blue = color.blue * f, alpha = color.alpha)
}

private fun lighten(color: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = color.red + (1f - color.red) * f,
        green = color.green + (1f - color.green) * f,
        blue = color.blue + (1f - color.blue) * f,
        alpha = color.alpha,
    )
}
