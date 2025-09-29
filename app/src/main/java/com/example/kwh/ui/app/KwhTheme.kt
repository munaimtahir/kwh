package com.example.kwh.ui.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    tertiary = Color(0xFFFFB300),
    background = Color(0xFFF1F8E9),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B5E20)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = Color.Black,
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color.Black,
    tertiary = Color(0xFFFFD54F),
    background = Color(0xFF0D1F12),
    surface = Color(0xFF1B3123),
    onSurface = Color(0xFFE8F5E9)
)

@Composable
fun KwhTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
