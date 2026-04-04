package me.xiaok.opencode.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Fallback palette (used when dynamicColor is disabled or unavailable)
// Inspired by code-editor aesthetics: deep blues, cool teals, warm amber accent
// ---------------------------------------------------------------------------

// Dark scheme tokens
val Blue80 = Color(0xFFB0C4FF)        // Soft periwinkle — primary (dark)
val Slate80 = Color(0xFFB8C0CC)       // Cool grey-blue — secondary (dark)
val Teal80 = Color(0xFF80CBC4)        // Muted teal — tertiary (dark)

// Light scheme tokens
val Blue40 = Color(0xFF1A3A6B)        // Deep navy — primary (light)
val Slate40 = Color(0xFF4A5568)       // Steel grey — secondary (light)
val Teal40 = Color(0xFF00897B)        // Rich teal — tertiary (light)

// Extended surface tokens for finer control
val SurfaceDimDark = Color(0xFF0E1218)
val SurfaceDark = Color(0xFF141A22)
val SurfaceBrightDark = Color(0xFF1A222D)
val SurfaceContainerDark = Color(0xFF1E2736)

val SurfaceDimLight = Color(0xFFF0F2F5)
val SurfaceLight = Color(0xFFF7F8FA)
val SurfaceBrightLight = Color(0xFFFFFFFF)
val SurfaceContainerLight = Color(0xFFE8ECF1)

// Status / semantic colors (shared across schemes)
val StatusConnected = Color(0xFF4CAF50)
val StatusConnecting = Color(0xFFFFC107)
val StatusError = Color(0xFFE53935)
val StatusIdle = Color(0xFF90A4AE)

// Accent for code/highlight elements
val AccentCyan = Color(0xFF00BCD4)
val AccentAmber = Color(0xFFFFAB00)
val AccentMint = Color(0xFF69F0AE)
