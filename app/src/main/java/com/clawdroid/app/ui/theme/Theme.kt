package com.clawdroid.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.clawdroid.app.core.config.AppConfigManager

private val ObsidianAstraColors = darkColorScheme(
    primary = AstraPrimary,
    onPrimary = AstraOnPrimary,
    primaryContainer = AstraPrimaryContainer,
    onPrimaryContainer = AstraOnPrimaryContainer,
    secondary = AstraSecondary,
    onSecondary = ObsidianSurfaceHighest,
    secondaryContainer = AstraSecondaryContainer,
    onSecondaryContainer = AstraOnSecondaryContainer,
    tertiary = AstraPrimary,
    onTertiary = ObsidianSurfaceHighest,
    background = ObsidianBackground,
    onBackground = ObsidianOnSurface,
    surface = ObsidianSurface,
    onSurface = ObsidianOnSurface,
    surfaceVariant = ObsidianSurfaceHighest,
    onSurfaceVariant = ObsidianOnSurfaceVariant,
    surfaceContainerLowest = ObsidianSurfaceLowest,
    surfaceContainerLow = ObsidianSurfaceLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = AstraError,
)

private val ClawMagicDarkColors = darkColorScheme(
    primary = Color(0xFF44D8F1),
    onPrimary = Color(0xFF00363E),
    primaryContainer = Color(0xFF00BCD4),
    onPrimaryContainer = Color(0xFF004650),
    secondary = Color(0xFFCDBDFF),
    onSecondary = Color(0xFF370096),
    secondaryContainer = Color(0xFF5203D5),
    onSecondaryContainer = Color(0xFFC0ACFF),
    tertiary = Color(0xFFFFB87B),
    onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFFF19640),
    onTertiaryContainer = Color(0xFF633400),
    background = Color(0xFF090909),
    onBackground = Color(0xFFDDE3E5),
    surface = Color(0xFF0E1416),
    onSurface = Color(0xFFDDE3E5),
    surfaceVariant = Color(0xFF2F3637),
    onSurfaceVariant = Color(0xFFBBC9CC),
    surfaceContainerLowest = Color(0xFF090F11),
    surfaceContainerLow = Color(0xFF161D1E),
    surfaceContainer = Color(0xFF1A2122),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF2F3637),
    outline = Color(0xFF869396),
    outlineVariant = Color(0xFF3C494C),
    error = Color(0xFFFFB4AB),
)

private val ClawMagicLightColors = lightColorScheme(
    primary = Color(0xFF2D5F93),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E7FF),
    onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF5C5F68),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E2EC),
    onSecondaryContainer = Color(0xFF191B22),
    tertiary = Color(0xFF785900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA1),
    onTertiaryContainer = Color(0xFF261900),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191B20),
    surfaceVariant = Color(0xFFE1E3EA),
    onSurfaceVariant = Color(0xFF454850),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3F9),
    surfaceContainer = Color(0xFFECEEF5),
    surfaceContainerHigh = Color(0xFFE5E8EF),
    surfaceContainerHighest = Color(0xFFDEE2EA),
    outline = Color(0xFF757983),
    outlineVariant = Color(0xFFC5C7D0),
    error = Color(0xFFBA1A1A),
)

private val ClawLightColors = lightColorScheme(
    primary = Color(0xFF245FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF0B315F),
    secondary = Color(0xFF58606A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E2E8),
    onSecondaryContainer = Color(0xFF171C22),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF171C22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171C22),
    surfaceVariant = Color(0xFFE7EAF0),
    onSurfaceVariant = Color(0xFF4B5562),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainer = Color(0xFFEAEFF5),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFD9E1EA),
    outline = Color(0xFF7B8490),
    outlineVariant = Color(0xFFC4CBD5),
    error = Color(0xFFBA1A1A),
)

private val MinimalistColors = darkColorScheme(
    primary = Color(0xFFE8EAED),
    onPrimary = Color(0xFF1B1D20),
    primaryContainer = Color(0xFF3C4043),
    onPrimaryContainer = Color(0xFFE8EAED),
    secondary = Color(0xFFBFC3C7),
    onSecondary = Color(0xFF202326),
    secondaryContainer = Color(0xFF2A2D30),
    onSecondaryContainer = Color(0xFFE2E5E8),
    background = Color(0xFF101112),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF151719),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF222528),
    onSurfaceVariant = Color(0xFFB9BEC4),
    surfaceContainerLowest = Color(0xFF0B0C0D),
    surfaceContainerLow = Color(0xFF181A1C),
    surfaceContainer = Color(0xFF1D2022),
    surfaceContainerHigh = Color(0xFF24272A),
    surfaceContainerHighest = Color(0xFF2B2F32),
    outline = Color(0xFF7D8288),
    outlineVariant = Color(0xFF383C40),
    error = Color(0xFFFFB4AB),
)

private val LiquidGlassLightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8ECFF),
    onPrimaryContainer = Color(0xFF002D52),
    secondary = Color(0xFF5E5CE6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E8FF),
    onSecondaryContainer = Color(0xFF17135B),
    tertiary = Color(0xFFFF375F),
    onTertiary = Color.White,
    background = Color(0xFFF4F8FF),
    onBackground = Color(0xFF111318),
    surface = Color(0xD9FFFFFF),
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xE8F2F7FF),
    onSurfaceVariant = Color(0xFF454A52),
    surfaceContainerLowest = Color(0xF7FFFFFF),
    surfaceContainerLow = Color(0xD9FFFFFF),
    surfaceContainer = Color(0xC9F7FAFF),
    surfaceContainerHigh = Color(0xBFEAF2FF),
    surfaceContainerHighest = Color(0xB2DCEBFF),
    outline = Color(0xFF73808E),
    outlineVariant = Color(0x73FFFFFF),
    error = Color(0xFFFF3B30),
)

private val LiquidGlassDarkColors = darkColorScheme(
    primary = Color(0xFF64D2FF),
    onPrimary = Color(0xFF002B3A),
    primaryContainer = Color(0xFF123E54),
    onPrimaryContainer = Color(0xFFD7F4FF),
    secondary = Color(0xFFBF5AF2),
    onSecondary = Color(0xFF32114A),
    secondaryContainer = Color(0xFF4B1F68),
    onSecondaryContainer = Color(0xFFF4D9FF),
    tertiary = Color(0xFFFF9F0A),
    onTertiary = Color(0xFF3A2200),
    background = Color(0xFF05070A),
    onBackground = Color(0xFFF5F7FB),
    surface = Color(0xB8141518),
    onSurface = Color(0xFFF5F7FB),
    surfaceVariant = Color(0xA6242930),
    onSurfaceVariant = Color(0xFFD7DCE5),
    surfaceContainerLowest = Color(0x66000000),
    surfaceContainerLow = Color(0x991C1C1E),
    surfaceContainer = Color(0xAA24272E),
    surfaceContainerHigh = Color(0xBB30343B),
    surfaceContainerHighest = Color(0xCC3A3F48),
    outline = Color(0xFF9DA7B6),
    outlineVariant = Color(0x5CFFFFFF),
    error = Color(0xFFFF453A),
)

private val CyberpunkColors = darkColorScheme(
    primary = Color(0xFF18E6FF),
    onPrimary = Color(0xFF001F27),
    primaryContainer = Color(0xFF083B4D),
    onPrimaryContainer = Color(0xFFD6FAFF),
    secondary = Color(0xFFFF4FDB),
    onSecondary = Color(0xFF3D0031),
    secondaryContainer = Color(0xFF5D174F),
    onSecondaryContainer = Color(0xFFFFD7F5),
    tertiary = Color(0xFFFFD166),
    onTertiary = Color(0xFF2F2300),
    tertiaryContainer = Color(0xFF4C3A08),
    onTertiaryContainer = Color(0xFFFFE8A6),
    background = Color(0xFF04010A),
    onBackground = Color(0xFFF9F4FF),
    surface = Color(0xFF090512),
    onSurface = Color(0xFFF9F4FF),
    surfaceVariant = Color(0xFF211228),
    onSurfaceVariant = Color(0xFFDCCEE5),
    surfaceContainerLowest = Color(0xFF020005),
    surfaceContainerLow = Color(0xFF0B0613),
    surfaceContainer = Color(0xFF120A1D),
    surfaceContainerHigh = Color(0xFF1C1028),
    surfaceContainerHighest = Color(0xFF271536),
    outline = Color(0xFFB389C0),
    outlineVariant = Color(0xFF553164),
    error = Color(0xFFFF5C7A),
)

private val JarvisColors = darkColorScheme(
    primary = Color(0xFF23E7FF),
    onPrimary = Color(0xFF00262D),
    primaryContainer = Color(0xFF063F4A),
    onPrimaryContainer = Color(0xFFCCFAFF),
    secondary = Color(0xFF8BD7E6),
    onSecondary = Color(0xFF08262C),
    secondaryContainer = Color(0xFF173D46),
    onSecondaryContainer = Color(0xFFD8F7FF),
    tertiary = Color(0xFFFFC857),
    onTertiary = Color(0xFF302300),
    tertiaryContainer = Color(0xFF493700),
    onTertiaryContainer = Color(0xFFFFE6A3),
    background = Color(0xFF01080B),
    onBackground = Color(0xFFE8FBFF),
    surface = Color(0xFF041015),
    onSurface = Color(0xFFE8FBFF),
    surfaceVariant = Color(0xFF12272E),
    onSurfaceVariant = Color(0xFFC7E7EF),
    surfaceContainerLowest = Color(0xFF000405),
    surfaceContainerLow = Color(0xFF061419),
    surfaceContainer = Color(0xFF0A1C22),
    surfaceContainerHigh = Color(0xFF102830),
    surfaceContainerHighest = Color(0xFF17343D),
    outline = Color(0xFF7BB8C3),
    outlineVariant = Color(0xFF25525C),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ClawDroidTheme(
    content: @Composable () -> Unit,
) {
    val themeKey by AppConfigManager.appThemeFlow.collectAsState()
    val context = LocalContext.current
    val dynamicColorsAvailable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val dynamicClawMagicColors = if (dynamicColorsAvailable) {
        dynamicDarkColorScheme(context)
    } else {
        ClawMagicDarkColors
    }
    val colorScheme = when (themeKey) {
        "claw_magic" -> dynamicClawMagicColors.copy(
            background = ClawMagicDarkColors.background,
            onBackground = ClawMagicDarkColors.onBackground,
            surface = ClawMagicDarkColors.surface,
            onSurface = ClawMagicDarkColors.onSurface,
            surfaceVariant = ClawMagicDarkColors.surfaceVariant,
            onSurfaceVariant = ClawMagicDarkColors.onSurfaceVariant,
            surfaceContainerLowest = ClawMagicDarkColors.surfaceContainerLowest,
            surfaceContainerLow = ClawMagicDarkColors.surfaceContainerLow,
            surfaceContainer = ClawMagicDarkColors.surfaceContainer,
            surfaceContainerHigh = ClawMagicDarkColors.surfaceContainerHigh,
            surfaceContainerHighest = ClawMagicDarkColors.surfaceContainerHighest,
            outline = ClawMagicDarkColors.outline,
            outlineVariant = ClawMagicDarkColors.outlineVariant,
        )
        "light" -> ClawLightColors
        "minimalist" -> MinimalistColors
        "liquid_glass_light" -> LiquidGlassLightColors
        "liquid_glass_dark" -> LiquidGlassDarkColors
        "cyberpunk" -> CyberpunkColors
        "jarvis" -> JarvisColors
        "dark" -> ObsidianAstraColors
        else -> dynamicClawMagicColors.copy(
            background = ClawMagicDarkColors.background,
            onBackground = ClawMagicDarkColors.onBackground,
            surface = ClawMagicDarkColors.surface,
            onSurface = ClawMagicDarkColors.onSurface,
            surfaceVariant = ClawMagicDarkColors.surfaceVariant,
            onSurfaceVariant = ClawMagicDarkColors.onSurfaceVariant,
            surfaceContainerLowest = ClawMagicDarkColors.surfaceContainerLowest,
            surfaceContainerLow = ClawMagicDarkColors.surfaceContainerLow,
            surfaceContainer = ClawMagicDarkColors.surfaceContainer,
            surfaceContainerHigh = ClawMagicDarkColors.surfaceContainerHigh,
            surfaceContainerHighest = ClawMagicDarkColors.surfaceContainerHighest,
            outline = ClawMagicDarkColors.outline,
            outlineVariant = ClawMagicDarkColors.outlineVariant,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClawDroidTypography,
        shapes = ClawDroidShapes,
        content = content,
    )
}
