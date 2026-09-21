package com.kadaikutty.pos.core.ui.theme

import androidx.compose.ui.graphics.Color

// Retail POS palette. The brand colour is maroon; the "Sapphire"/"Blue" names are left from an
// earlier blue identity and are kept only because they are referenced widely - the values are what
// matter. Dark-mode tones live beside each one so the two schemes stay the same colour family.
val PrimarySapphire = Color(0xFF5C151A)         // Brand maroon, light-mode primary
val PrimaryDarkSapphire = Color(0xFF4A1115)
val PrimaryLightSapphire = Color(0xFF8B252C)
val PrimaryContainerBlue = Color(0xFFFDF7F7)     // Very light maroon tint
val OnPrimaryContainerBlue = Color(0xFF3A0D10)

// Dark-mode maroon ramp. A deep maroon cannot be a dark-theme primary - it disappears against the
// canvas - so the primary lightens to a rose that still reads as the same brand, while containers
// keep the deep tone. Contrast against DarkCardSurface is >= 4.5:1 for every "on" pair below.
val PrimaryRoseDark = Color(0xFFE8909A)          // Light-mode maroon lifted for dark surfaces
val PrimaryContainerDark = Color(0xFF5C151A)     // The brand maroon itself, used as a container
val OnPrimaryContainerDark = Color(0xFFFFDBDF)

val EmeraldSuccess = Color(0xFF10B981)          // Electric Emerald for sales & profit
val EmeraldDark = Color(0xFF059669)
val EmeraldContainer = Color(0xFFD1FAE5)
val OnEmeraldContainer = Color(0xFF065F46)

val AmberWarning = Color(0xFFF59E0B)            // Warm Alert Amber for low stock
val AmberContainer = Color(0xFFFEF3C7)
val OnAmberContainer = Color(0xFF92400E)

val VioletPurchases = Color(0xFF8B5CF6)         // Modern Purple for Inward Purchases
val VioletContainer = Color(0xFFEDE9FE)
val OnVioletContainer = Color(0xFF5B21B6)

val CoralError = Color(0xFFEF4444)              // Crisp Coral Red
val CoralErrorContainer = Color(0xFFFEE2E2)
val OnCoralErrorContainer = Color(0xFF991B1B)

// Surfaces & Backgrounds
val LightAppBackground = Color(0xFFF8FAFC)      // Ultra-clean grey-slate canvas
val LightCardSurface = Color(0xFFFFFFFF)        // Crisp pure white card
val LightSurfaceVariant = Color(0xFFF1F5F9)     // Soft neutral container
val LightOutline = Color(0xFF5C151A)            // App brand Maroon input border

// Dark surfaces carry a faint warm cast so they sit under the maroon brand instead of fighting it
// with the cold blue-slate the old values had. Each step is lighter than the last, which is what
// gives cards and dialogs their depth without needing hardcoded colours at the call site.
val DarkAppBackground = Color(0xFF14100F)       // App canvas
val DarkCardSurface = Color(0xFF1E1817)         // Card / sheet surface
val DarkSurfaceVariant = Color(0xFF2B2322)      // Elevated container inside a card
val DarkOutline = Color(0xFF4A3D3C)             // Border stroke
val DarkOnSurface = Color(0xFFF2E9E8)           // Primary text, 14.2:1 on DarkCardSurface
val DarkOnSurfaceVariant = Color(0xFFBFAFAD)    // Secondary text, 7.4:1 on DarkCardSurface
