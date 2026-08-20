package com.focusdhikr.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * A quiet palette.
 *
 * Warm near-black and parchment rather than pure black and white, one muted
 * gold accent, and nothing else. No progress bars racing to full, no confetti,
 * no streak flames. The visual language should feel closer to a well-set book
 * than to a game. See requirement 12.
 */
object FocusColors {
    val Ink = Color(0xFF12100E)
    val InkElevated = Color(0xFF1C1917)
    val InkSubtle = Color(0xFF2A2624)

    val Parchment = Color(0xFFFAF9F6)
    val ParchmentElevated = Color(0xFFF2F0EA)

    val TextOnInk = Color(0xFFE8E3D8)
    val TextOnInkMuted = Color(0xFFA8A199)
    val TextOnParchment = Color(0xFF1C1917)
    val TextOnParchmentMuted = Color(0xFF6B645C)

    /** The single accent. Used sparingly: remaining time, the active step, dhikr. */
    val Gold = Color(0xFFC9A227)
    val GoldSoft = Color(0xFFD9BE63)

    /** Never red-for-shame. This is the "you have reached your limit" tone. */
    val Clay = Color(0xFFB07156)
}

private val DarkScheme = darkColorScheme(
    primary = FocusColors.Gold,
    onPrimary = FocusColors.Ink,
    secondary = FocusColors.GoldSoft,
    onSecondary = FocusColors.Ink,
    background = FocusColors.Ink,
    onBackground = FocusColors.TextOnInk,
    surface = FocusColors.InkElevated,
    onSurface = FocusColors.TextOnInk,
    surfaceVariant = FocusColors.InkSubtle,
    onSurfaceVariant = FocusColors.TextOnInkMuted,
    outline = FocusColors.InkSubtle,
    error = FocusColors.Clay,
    onError = FocusColors.TextOnInk,
)

private val LightScheme = lightColorScheme(
    primary = FocusColors.Gold,
    onPrimary = FocusColors.Parchment,
    secondary = FocusColors.Clay,
    onSecondary = FocusColors.Parchment,
    background = FocusColors.Parchment,
    onBackground = FocusColors.TextOnParchment,
    surface = FocusColors.ParchmentElevated,
    onSurface = FocusColors.TextOnParchment,
    surfaceVariant = Color(0xFFE7E3DA),
    onSurfaceVariant = FocusColors.TextOnParchmentMuted,
    outline = Color(0xFFD8D3C8),
    error = FocusColors.Clay,
    onError = FocusColors.Parchment,
)

/**
 * Serif for anything meant to be read slowly, sans for anything meant to be
 * scanned. Both are system families, so the app ships no font binaries.
 */
private val FocusTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 42.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 21.sp,
        lineHeight = 29.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
    ),
)

/** Spacing scale. Generous by default; whitespace is most of the calm. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

@Composable
fun FocusDhikrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** The gate is always dark: it appears over other apps, often at night. */
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = forceDark || darkTheme
    val scheme = if (useDark) DarkScheme else LightScheme
    val view = androidx.compose.ui.platform.LocalView.current

    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDark
                    isAppearanceLightNavigationBars = !useDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = FocusTypography,
        content = content,
    )
}
