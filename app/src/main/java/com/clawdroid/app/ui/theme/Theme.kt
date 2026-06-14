package com.clawdroid.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ClawDroidDarkScheme = darkColorScheme(
    primary            = FireRed,
    onPrimary          = SoftWhite,
    primaryContainer   = CardDark,
    onPrimaryContainer = SoftWhite,

    secondary          = EmberOrange,
    onSecondary        = DeepBlack,
    secondaryContainer = ElevatedDark,
    onSecondaryContainer = SoftWhite,

    tertiary           = MoltenYellow,
    onTertiary         = DeepBlack,
    tertiaryContainer  = CardDark,
    onTertiaryContainer = MoltenYellow,

    background         = DeepBlack,
    onBackground       = SoftWhite,

    surface            = NightSurface,
    onSurface          = SoftWhite,
    surfaceVariant     = CardDark,
    onSurfaceVariant   = MutedGray,

    outline            = DimGray,
    outlineVariant     = GlassBorderDim,

    error              = ErrorRed,
    onError            = DeepBlack,
    errorContainer     = DeepEmber,
    onErrorContainer   = SoftWhite,

    inverseSurface     = SoftWhite,
    inverseOnSurface   = DeepBlack,
    inversePrimary     = DeepEmber,

    scrim              = DeepBlack,
)

@Composable
fun ClawDroidTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ClawDroidDarkScheme,
        typography = ClawDroidTypography,
        content = content,
    )
}
