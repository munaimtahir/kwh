package com.example.kwh.ui.app

import android.os.Build
import android.os.Build.VERSION_CODES
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B7A4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA3F2C4),
    onPrimaryContainer = Color(0xFF03371B),
    secondary = Color(0xFF3D5A73),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4F5),
    onSecondaryContainer = Color(0xFF142436),
    tertiary = Color(0xFFE57F17),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF271900),
    background = Color(0xFFFBF9F5),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFCFBF8),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE0E2D8),
    onSurfaceVariant = Color(0xFF43473F),
    outline = Color(0xFF74796F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF87D6A4),
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF01522A),
    onPrimaryContainer = Color(0xFFA3F2C4),
    secondary = Color(0xFFBCC9D9),
    onSecondary = Color(0xFF26323F),
    secondaryContainer = Color(0xFF394656),
    onSecondaryContainer = Color(0xFFD6E4F5),
    tertiary = Color(0xFFFFB951),
    onTertiary = Color(0xFF412C00),
    tertiaryContainer = Color(0xFF5D4100),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF121411),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF181C18),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF42473E),
    onSurfaceVariant = Color(0xFFC4C8BD),
    outline = Color(0xFF8E9388)
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(letterSpacing = 0.2.sp)
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KwhTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
