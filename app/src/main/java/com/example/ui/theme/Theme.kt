package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudyLockColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = Color.White,
    primaryContainer = ElectricIndigoDark,
    onPrimaryContainer = Color.White,
    secondary = ElectricIndigoLight,
    onSecondary = ObsidianBg,
    secondaryContainer = SlateElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonEmerald,
    onTertiary = Color.White,
    tertiaryContainer = NeonEmeraldDark,
    onTertiaryContainer = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    outlineVariant = CardBorder,
    error = CoralRed,
    onError = Color.White,
    errorContainer = CoralRedDark,
    onErrorContainer = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudyLockColorScheme,
        typography = Typography,
        content = content
    )
}
