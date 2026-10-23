package com.example.installerxui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CyanBlue = Color(0xFF00BCD4)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F6AC9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF5B5D72),
    tertiary = Color(0xFF77536D),
    tertiaryContainer = Color(0xFFFFD7F0),
    onTertiaryContainer = Color(0xFF2C1129),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainer = Color(0xFFF1EFF7),
    surfaceContainerHighest = Color(0xFFE6E1EC),
    surfaceBright = Color(0xFFFBF8FF),
    background = Color(0xFFFBF8FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF182778),
    primaryContainer = Color(0xFF344792),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC5C5DD),
    tertiary = Color(0xFFE5BAD8),
    tertiaryContainer = Color(0xFF5C3B53),
    onTertiaryContainer = Color(0xFFFFD7F0),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC6C6D0),
    surfaceContainer = Color(0xFF1E1E23),
    surfaceContainerHighest = Color(0xFF37363E),
    surfaceBright = Color(0xFF3A3A40),
    background = Color(0xFF131318),
)

@Composable
fun InstallerXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
