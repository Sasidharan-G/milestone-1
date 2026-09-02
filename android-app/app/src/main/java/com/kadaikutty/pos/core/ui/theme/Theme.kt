package com.kadaikutty.pos.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
    onSurfaceVariant = Color(0xFF334155),
    outline = LightOutline,
    outlineVariant = Color(0xFFCBD5E1)
)

private val DarkColors = darkColorScheme(
    primary = PrimaryLightSapphire,
    onPrimary = Color(0xFF0B0F19),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFBFDBFE),
    secondary = EmeraldSuccess,
    onSecondary = Color(0xFF064E3B),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF2E1065),
    tertiaryContainer = Color(0xFF5B21B6),
    onTertiaryContainer = Color(0xFFDDD6FE),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    background = DarkAppBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = DarkCardSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = DarkOutline,
    outlineVariant = Color(0xFF475569)
)

private val AmoledColors = darkColorScheme(
    primary = Color(0xFF38BDF8), // Vivid Cyan
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF34D399), // Neon Emerald
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFFFBBF24), // Vivid Amber
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    background = Color(0xFF000000), // Pure OLED Black Canvas
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF18181B),
    onSurfaceVariant = Color(0xFFE4E4E7),
    outline = Color(0xFF52525B),
    outlineVariant = Color(0xFF3F3F46)
)

@Composable
fun BillingTheme(
    themeMode: String = "System",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = when {
        themeMode == "Amoled" -> AmoledColors
        themeMode == "Dark" || (themeMode == "System" && darkTheme) -> DarkColors
        else -> LightColors
    }
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
