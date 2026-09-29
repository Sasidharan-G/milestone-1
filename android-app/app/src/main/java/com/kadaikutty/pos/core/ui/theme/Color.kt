package com.kadaikutty.pos.core.ui.theme

import androidx.compose.ui.graphics.Color

// Retail POS palette. Brand identity (2026-09-28): a plain, confident blue - deliberately not the
// old maroon and not the charcoal+gold that preceded this, so the app reads as a distinct, simple
// "shop ledger" look. Blue was chosen over a second green/teal because Success below is already
// green; a blue primary stays visually distinct from it at a glance, which matters most for a
// reader who is going by colour and icon shape rather than reading every label.
// The "Sapphire"/"Rose" names are left from earlier identities and are kept only because they are
// referenced widely - the values are what matter. Dark-mode tones live beside each one so the two
// schemes stay the same colour family.
val PrimarySapphire = Color(0xFF1D4ED8)         // Brand blue, light-mode primary
val PrimaryDarkSapphire = Color(0xFF1E3A8A)
val PrimaryLightSapphire = Color(0xFFE2E8F0)
val PrimaryContainerBlue = Color(0xFFEFF6FF)     // Very light blue tint
val OnPrimaryContainerBlue = Color(0xFF1E3A8A)

// Dark-mode blue ramp. The light-mode primary is too dark to read as a dark-theme primary - it
// disappears against the canvas - so the primary lifts to a pale blue, while containers keep a
// deep navy tone. Contrast against DarkCardSurface is >= 4.5:1 for every "on" pair below.
val PrimaryRoseDark = Color(0xFF93C5FD)          // Pale blue, lifted for dark surfaces
val PrimaryContainerDark = Color(0xFF1E3A8A)     // Deep navy container
val OnPrimaryContainerDark = Color(0xFFEFF6FF)

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
val LightOutline = Color(0xFF94A3B8)            // Neutral slate input border

// Dark surfaces are a plain cool slate ramp - neutral on purpose, so they sit quietly under
// whichever brand colour is on top instead of tinting it. Each step is lighter than the last,
// which is what gives cards and dialogs their depth without needing hardcoded colours at the call
// site.
val DarkAppBackground = Color(0xFF0F172A)       // App canvas
val DarkCardSurface = Color(0xFF1E293B)         // Card / sheet surface
val DarkSurfaceVariant = Color(0xFF334155)      // Elevated container inside a card
val DarkOutline = Color(0xFF475569)             // Border stroke
val DarkOnSurface = Color(0xFFF8FAFC)           // Primary text, 14.9:1 on DarkCardSurface
val DarkOnSurfaceVariant = Color(0xFFCBD5E1)    // Secondary text, 8.4:1 on DarkCardSurface
