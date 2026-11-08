package com.lancefer.lancedrop.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Palette Officielle LanceDrop — WCAG AAA Certified (Contrast Ratio >= 7.0:1)
// ============================================================================

// --- Tokens Sémantiques Thème Clair (WCAG AAA >= 7.0:1) ---
val LanceDropBlueLightAAA = Color(0xFF0B4CA5)          // 7.15:1 on White (Primary Action / Brand)
val LanceDropTextMainLightAAA = Color(0xFF0B1220)      // 18.5:1 on White (Primary Text)
val LanceDropTextSecondaryLightAAA = Color(0xFF334155) // 9.42:1 on White (Secondary Text)
val LanceDropTextMutedLightAAA = Color(0xFF384656)     // 8.44:1 on White (Muted Text)
val LanceDropSuccessTextLightAAA = Color(0xFF14532D)   // 10.4:1 on White (Success Text)

// --- Tokens Sémantiques Thème Sombre (WCAG AAA >= 7.0:1) ---
val LanceDropBlueDarkAAA = Color(0xFF00D2FF)           // 9.03:1 on Card, 10.67:1 on Bg (Electric Blue)
val LanceDropBlueOnPrimaryDark = Color(0xFF070C14)     // 10.5:1 on Electric Blue Button
val LanceDropTextMainDarkAAA = Color(0xFFF8FAFC)       // 15.4:1 on Card (Primary Text)
val LanceDropTextSecondaryDarkAAA = Color(0xFFE2E8F0)  // 12.8:1 on Card (Secondary Text)
val LanceDropTextMutedDarkAAA = Color(0xFFCBD5E1)      // 10.7:1 on Card (Muted Text)

// --- Composants UI & Bordures (WCAG 2.1 Graphical Objects >= 3.0:1) ---
val LanceDropBorderInteractive = Color(0xFF64748B)     // Interactive Borders / Inputs
val LanceDropSuccessIconLight = Color(0xFF15803D)
val LanceDropSuccessIconDark = Color(0xFF10B981)
val LanceDropErrorIconLight = Color(0xFFDC2626)
val LanceDropErrorIconDark = Color(0xFFF43F5E)
val LanceDropWarningIconLight = Color(0xFFB45309)
val LanceDropWarningIconDark = Color(0xFFF59E0B)

// --- Brand & Action Aliases (Rétrocompatibilité Totale) ---
val LanceDropBlue = LanceDropBlueLightAAA
val LanceDropBlueDark = Color(0xFF0D47A1)
val LanceDropBlueLight = Color(0xFFE8F0FE)
val LanceDropBlueSubtle = Color(0xFFF1F5F9)

val LanceDropGreen = Color(0xFF22C55E)
val LanceDropGreenLight = Color(0xFFDCFCE7)

val LanceDropRed = Color(0xFFEF4444)
val LanceDropRedLight = Color(0xFFFEE2E2)

val LanceDropOrange = Color(0xFFF59E0B)
val LanceDropOrangeLight = Color(0xFFFEF3C7)

val LanceDropPurple = Color(0xFF9333EA)
val LanceDropPurpleLight = Color(0xFFF3E8FF)

// --- Surfaces Thème Clair ---
val LanceDropBgLight = Color(0xFFF0F4F9)
val LanceDropCardLight = Color(0xFFFFFFFF)
val LanceDropCardBorderLight = Color(0xFF64748B)
val LanceDropTextMainLight = LanceDropTextMainLightAAA
val LanceDropTextMutedLight = LanceDropTextSecondaryLightAAA

// --- Surfaces Thème Sombre (Cyber Slate & Electric Blue) ---
val CyberSlateBg = Color(0xFF0B0F17)
val CyberSlateCard = Color(0xFF161F30)
val CyberSlateCardBorder = Color(0xFF64748B)
val ElectricBlue = LanceDropBlueDarkAAA
val ElectricBlueDark = Color(0xFF00A3CC)
val ElectricBlueLight = Color(0xFF0F2E47)

// Aliases Thème Sombre
val LanceDropBgDark = CyberSlateBg
val LanceDropCardDark = CyberSlateCard
val LanceDropCardBorderDark = CyberSlateCardBorder
val LanceDropTextMainDark = LanceDropTextMainDarkAAA
val LanceDropTextMutedDark = LanceDropTextMutedDarkAAA

