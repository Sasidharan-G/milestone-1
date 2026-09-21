package com.kadaikutty.pos.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Small tinted badges (payment mode, status pills) that the Material colour roles do not cover.
 *
 * Read from the active scheme rather than from `isSystemInDarkTheme()`, because this app lets the
 * shop force Light or Dark in Settings regardless of the system. The scheme is the only thing that
 * knows which one actually won.
 */
@Composable
@ReadOnlyComposable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Background and text for a payment-mode badge, legible on either canvas. */
@Composable
@ReadOnlyComposable
fun paymentChipColors(paymentMode: String): Pair<Color, Color> {
    val dark = isDarkTheme()
    return when (paymentMode.uppercase()) {
        "CASH" -> if (dark) Color(0xFF14432F) to Color(0xFF86EFAC) else Color(0xFFD1FAE5) to Color(0xFF065F46)
        "UPI" -> if (dark) Color(0xFF1B3358) to Color(0xFF93C5FD) else Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        else -> if (dark) Color(0xFF4A3212) to Color(0xFFFCD34D) else Color(0xFFFEF3C7) to Color(0xFF92400E)
    }
}

/** Background and text for the staff-role pill. */
@Composable
@ReadOnlyComposable
fun roleChipColors(role: String): Pair<Color, Color> {
    val dark = isDarkTheme()
    return when (role.uppercase()) {
        "STORE_MANAGER" -> if (dark) Color(0xFF2E2350) to Color(0xFFC4B5FD) else Color(0xFFEDE9FE) to Color(0xFF6D28D9)
        "ADMIN" -> if (dark) Color(0xFF4A3212) to Color(0xFFFCD34D) else Color(0xFFFEF3C7) to Color(0xFFB45309)
        else -> if (dark) Color(0xFF1B3358) to Color(0xFF93C5FD) else Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
    }
}

/** Background and text for a staff account's status pill: pending, deactivated, or active. */
@Composable
@ReadOnlyComposable
fun statusChipColors(isPending: Boolean, isInactive: Boolean): Pair<Color, Color> {
    val dark = isDarkTheme()
    return when {
        isPending -> if (dark) Color(0xFF4A3212) to Color(0xFFFCD34D) else Color(0xFFFFFBEB) to Color(0xFFD97706)
        isInactive -> if (dark) Color(0xFF4C1D1D) to Color(0xFFFCA5A5) else Color(0xFFFEE2E2) to Color(0xFFDC2626)
        else -> if (dark) Color(0xFF14432F) to Color(0xFF86EFAC) else Color(0xFFDCFCE7) to Color(0xFF15803D)
    }
}
