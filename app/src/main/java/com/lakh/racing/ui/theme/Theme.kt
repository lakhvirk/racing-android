package com.lakh.racing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RaceOrange = Color(0xFFFF3D00)
val RaceYellow = Color(0xFFFFC400)
val RaceGreen = Color(0xFF2E7D32)

private val colors = darkColorScheme(
    primary = RaceOrange,
    onPrimary = Color.White,
    primaryContainer = RaceOrange,
    onPrimaryContainer = Color.White,
    secondary = RaceYellow,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A3B00),
    onSecondaryContainer = RaceYellow,
    background = Color(0xFF101114),
    surface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFF26292E),
    error = Color(0xFFFF5252),
)

/** Dark, high-contrast theme: readable in a bouncing car and at night. */
@Composable
fun RacingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
