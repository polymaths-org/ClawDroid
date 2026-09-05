package com.clawdroid.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun ClawDroidTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ObsidianAstraColors,
        typography = ClawDroidTypography,
        content = content,
    )
}
