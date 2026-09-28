package com.kadaikutty.pos.core.ui.theme

import androidx.compose.ui.graphics.Color

// Retail POS palette. Brand identity (2026-09-28): charcoal + gold, matching the app's own
// launcher icon (a dark near-black card with a gold crown/border) instead of the earlier maroon.
// The "Sapphire"/"Rose" names are left from even earlier identities and are kept only because
// they are referenced widely - the values are what matter. Dark-mode tones live beside each one
// so the two schemes stay the same colour family.
val PrimarySapphire = Color(0xFF0C1018)         // Brand charcoal, light-mode primary (icon card colour)
val PrimaryDarkSapphire = Color(0xFF05070A)
val PrimaryLightSapphire = Color(0xFF3A2E22)
val PrimaryContainerBlue = Color(0xFFF3EDE3)     // Very light warm-gold tint
val OnPrimaryContainerBlue = Color(0xFF241A08)

// Dark-mode charcoal ramp. A near-black cannot be a dark-theme primary - it disappears against the
// canvas - so the primary switches to the icon's own gold, while containers keep the deep charcoal
// tone. Contrast against DarkCardSurface is >= 4.5:1 for every "on" pair below.
val PrimaryRoseDark = Color(0xFFD9B77A)          // Icon's gold, lifted for dark surfaces
val PrimaryContainerDark = Color(0xFF3A2E10)     // Deep gold-brown container
val OnPrimaryContainerDark = Color(0xFFF3EDE3)

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

// Home-screen menu tile accents (Ledger, Reports) - light AND dark pairs, same reasoning as the
// dark-mode charcoal-and-gold ramp above: a light pastel container would look like a mistake on a dark canvas.
val SkyContainer = Color(0xFFE0F2FE)
val OnSkyContainer = Color(0xFF075985)
val DarkSkyContainer = Color(0xFF0C4A6E)
val DarkOnSkyContainer = Color(0xFFE0F2FE)

val TealContainer = Color(0xFFCCFBF1)
val OnTealContainer = Color(0xFF115E59)
val DarkTealContainer = Color(0xFF115E59)
val DarkOnTealContainer = Color(0xFFCCFBF1)

// Surfaces & Backgrounds
val LightAppBackground = Color(0xFFF8FAFC)      // Ultra-clean grey-slate canvas
val LightCardSurface = Color(0xFFFFFFFF)        // Crisp pure white card
val LightSurfaceVariant = Color(0xFFF1F5F9)     // Soft neutral container
val LightOutline = Color(0xFF0C1018)            // App brand charcoal input border

// Dark surfaces carry a faint warm cast so they sit under the charcoal-and-gold brand instead of
// fighting it with a cold blue-slate. Each step is lighter than the last, which is what gives
// cards and dialogs their depth without needing hardcoded colours at the call site.
val DarkAppBackground = Color(0xFF14100F)       // App canvas
val DarkCardSurface = Color(0xFF1E1817)         // Card / sheet surface
val DarkSurfaceVariant = Color(0xFF2B2322)      // Elevated container inside a card
val DarkOutline = Color(0xFF4A3D3C)             // Border stroke
val DarkOnSurface = Color(0xFFF2E9E8)           // Primary text, 14.2:1 on DarkCardSurface
val DarkOnSurfaceVariant = Color(0xFFBFAFAD)    // Secondary text, 7.4:1 on DarkCardSurface
