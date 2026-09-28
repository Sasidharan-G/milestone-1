package com.kadaikutty.pos.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = PrimarySapphire,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerBlue,
    onPrimaryContainer = OnPrimaryContainerBlue,
    secondary = EmeraldDark,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = VioletPurchases,
    onTertiary = Color.White,
    tertiaryContainer = VioletContainer,
    onTertiaryContainer = OnVioletContainer,
    error = CoralError,
    errorContainer = CoralErrorContainer,
    onErrorContainer = OnCoralErrorContainer,
    background = LightAppBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightCardSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF0F172A),
    outline = LightOutline,
    outlineVariant = PrimaryLightSapphire
)

private val DarkColors = darkColorScheme(
    primary = PrimaryRoseDark,
    onPrimary = Color(0xFF241A08),
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = Color(0xFF6EE7B7),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFC6F7E2),
    tertiary = Color(0xFFF0B27A),
    onTertiary = Color(0xFF45240A),
    tertiaryContainer = Color(0xFF6B3A12),
    onTertiaryContainer = Color(0xFFFFE3C9),
    error = Color(0xFFFFA8A0),
    onError = Color(0xFF5C0F0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkAppBackground,
    onBackground = DarkOnSurface,
    surface = DarkCardSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF3A2F2E),
    scrim = Color(0xFF000000)
)

/**
 * Brand accents beyond Material's 4 built-in roles (primary/secondary/tertiary/error), for screens
 * that need a couple more distinct icon-tile colours (e.g. the Home menu grid). Each needs both a
 * light and a dark value - unlike [MaterialTheme.colorScheme], a raw `Color(0xFF...)` constant
 * never flips with the theme, which is exactly what makes a screen look wrong in one of the two modes.
 */
data class ExtendedColors(
    val ledgerContainer: Color,
    val onLedgerContainer: Color,
    val reportsContainer: Color,
    val onReportsContainer: Color
)

private val LightExtendedColors = ExtendedColors(
    ledgerContainer = SkyContainer, onLedgerContainer = OnSkyContainer,
    reportsContainer = TealContainer, onReportsContainer = OnTealContainer
)

private val DarkExtendedColors = ExtendedColors(
    ledgerContainer = DarkSkyContainer, onLedgerContainer = DarkOnSkyContainer,
    reportsContainer = DarkTealContainer, onReportsContainer = DarkOnTealContainer
)

val LocalExtendedColors = androidx.compose.runtime.staticCompositionLocalOf { LightExtendedColors }

@Composable
fun BillingTheme(
    themeMode: String = "Light",
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // "Amoled" is a retired theme; any device still holding that preference falls through to
    // Dark rather than losing its choice.
    val colors = if (themeMode == "Dark" || themeMode == "Amoled" || darkTheme) DarkColors else LightColors
    val extendedColors = if (colors === DarkColors) DarkExtendedColors else LightExtendedColors

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                val isLight = colors == LightColors
                // Every screen has a charcoal (or dark) header under the status bar, in the light theme
                // too, so its clock and icons must be white. Dark icons there were unreadable.
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = isLight
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colors,
            shapes = androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            ),
            typography = Typography,
            content = content
        )
    }
}
