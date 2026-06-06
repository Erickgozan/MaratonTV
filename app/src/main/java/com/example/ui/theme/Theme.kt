package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BloodRed,
    secondary = CardSlate,
    tertiary = YellowStar,
    background = DarkCharcoal,
    surface = CardSlate,
    onPrimary = Color.White,
    onSecondary = TextLight,
    onTertiary = DarkCharcoal,
    onBackground = TextLight,
    onSurface = TextLight
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
