package com.clawdroid.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Obsidian Astra Theme Core Colors ──
val ObsidianBackground = Color(0xFF111416)
val ObsidianSurface = Color(0xFF111416)
val ObsidianSurfaceLowest = Color(0xFF0C0F11)
val ObsidianSurfaceLow = Color(0xFF191C1E)
val ObsidianSurfaceContainer = Color(0xFF1D2022)
val ObsidianSurfaceHigh = Color(0xFF272A2C)
val ObsidianSurfaceHighest = Color(0xFF323537)
val ObsidianOnSurface = Color(0xFFE1E2E5)
val ObsidianOnSurfaceVariant = Color(0xFFC3C6D0)
val ObsidianOutline = Color(0xFF8D9199)
val ObsidianOutlineVariant = Color(0xFF43474F)
val AstraPrimary = Color(0xFFD3E2FF)
val AstraPrimaryContainer = Color(0xFFA8C7FA)
val AstraOnPrimary = Color(0xFF0A315B)
val AstraOnPrimaryContainer = Color(0xFF33537F)
val AstraSecondary = Color(0xFFC5C7C5)
val AstraSecondaryContainer = Color(0xFF444746)
val AstraOnSecondaryContainer = Color(0xFFB3B6B4)
val AstraError = Color(0xFFFFB4AB)

// ── Reconciled Cyberpunk/Glassmorphism Theme Accents (Mapped to Astra palette) ──
val DeepBlack       = ObsidianBackground
val NightSurface    = ObsidianSurface
val CardDark        = ObsidianSurfaceContainer
val ElevatedDark    = ObsidianSurfaceHigh

val FireRed         = AstraPrimaryContainer     // Mapped to Astra soft primary blue-grey
val EmberOrange     = AstraSecondary            // Mapped to Astra secondary grey
val MoltenYellow    = AstraOnPrimaryContainer   // Mapped to Astra dark blue
val LavaGlow        = AstraOnPrimaryContainer
val DeepEmber       = ObsidianSurfaceLowest

val NeonBlue        = AstraPrimaryContainer
val NeonCyan        = AstraPrimary
val ElectricBlue    = AstraPrimaryContainer
val DeepNavy        = ObsidianSurfaceLowest
val ActivePurple    = Color(0xFFB0A2F8)         // Soft lavender/purple for voice highlights

val SoftWhite       = ObsidianOnSurface
val MutedGray       = ObsidianOnSurfaceVariant
val DimGray         = ObsidianOutline
val ErrorRed        = AstraError

// ── Glassmorphic fills (Muted white-translucency instead of neon cyan) ──
val GlassFill       = Color(0x14FFFFFF)         // 8% white glass fill
val GlassFillMedium = Color(0x24FFFFFF)         // 14% white glass fill
val GlassFillStrong = Color(0x38FFFFFF)         // 22% white glass fill
val GlassBorder     = Color(0x33FFFFFF)         // 20% white border
val GlassBorderDim  = Color(0x1FFFFFFF)         // 12% white border dim

// ── Voice colors (Soft Astra tones matching Listening/Speaking visualizer states) ──
val UserVoiceBlue   = AstraPrimaryContainer
val UserVoiceCyan   = AstraPrimary
val AgentVoiceRed   = Color(0xFFB0A2F8)         // Soft lavender
val AgentVoiceGold  = Color(0xFF8E7CFA)         // Muted deep purple
