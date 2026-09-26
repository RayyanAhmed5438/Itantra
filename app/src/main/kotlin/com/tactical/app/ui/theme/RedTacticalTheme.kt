package com.tactical.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RedTacticalBackground = Color(0xFF0B0C0E)
val RedTacticalSurface = Color(0xFF16181D)
val RedTacticalSurfaceBorder = Color(0xFF2C2F36)
val RedTacticalPrimary = Color(0xFFE53935)
val RedTacticalPrimaryBright = Color(0xFFFF3B30)
val RedTacticalDarkBorder = Color(0xFF4A1A1A)
val RedTacticalTextPrimary = Color(0xFFFFFFFF)
val RedTacticalTextSecondary = Color(0xFFA0A5B5)
val RedTacticalStatusGreen = Color(0xFF4CAF50)
val RedTacticalStatusYellow = Color(0xFFFFC107)
val RedTacticalVoiceOrange = Color(0xFFFF9800)

val SquadBlueBackground = Color(0xFF061A31)
val SquadBlueSurface = Color(0xFF0B2A4A)
val SquadBlueSurfaceRaised = Color(0xFF10385D)
val SquadBlueBorder = Color(0xFF1E527D)
val SquadBluePrimary = Color(0xFF008CFF)
val SquadBlueGlow = Color(0xFF19BFFF)
val SquadHoldRed = Color(0xFFFF3656)
val SquadHoldRedGlow = Color(0xFFFF4B68)
val SquadTransmitOrange = Color(0xFFFF9800)
val SquadTransmitGlow = Color(0xFFFFA62B)

private val DarkColorScheme = darkColorScheme(
    primary = RedTacticalPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1010),
    onPrimaryContainer = Color(0xFFFFD2D2),
    secondary = RedTacticalPrimaryBright,
    onSecondary = Color.White,
    background = RedTacticalBackground,
    onBackground = RedTacticalTextPrimary,
    surface = RedTacticalSurface,
    onSurface = RedTacticalTextPrimary,
    surfaceVariant = Color(0xFF20232A),
    onSurfaceVariant = RedTacticalTextSecondary,
    outline = RedTacticalSurfaceBorder,
    error = RedTacticalPrimaryBright,
    onError = Color.White
)

@Composable
fun RedTacticalTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
