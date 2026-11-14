package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrokerVaultColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = CyanSecondary,
    onSecondary = Color(0xFF083344),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = AmberWarning,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = OnCrimsonContainer,
    background = VaultObsidian,
    onBackground = TextPrimary,
    surface = VaultSurface,
    onSurface = TextPrimary,
    surfaceVariant = VaultSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = VaultBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BrokerVaultColorScheme,
        typography = Typography,
        content = content
    )
}
