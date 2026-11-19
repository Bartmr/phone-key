package com.bartmr.phonekey.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MetalLightColorScheme = lightColorScheme(
    primary = Color(0xFF263238),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB0BEC5),
    onPrimaryContainer = Color(0xFF0F1518),
    secondary = Color(0xFF455A64),
    onSecondary = Color(0xFFF5F7F8),
    secondaryContainer = Color(0xFFCBD3D8),
    onSecondaryContainer = Color(0xFF0F1518),
    tertiary = Color(0xFF546E7A),
    onTertiary = Color(0xFFF5F7F8),
    background = Color(0xFFECEEF0),
    onBackground = Color(0xFF10141A),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF10141A),
    surfaceVariant = Color(0xFFD6DCE0),
    onSurfaceVariant = Color(0xFF333B40),
    outline = Color(0xFF6B747A),
    outlineVariant = Color(0xFFAAB3B9),
)

private val MetalDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB0BEC5),
    onPrimary = Color(0xFF15191C),
    primaryContainer = Color(0xFF3A4147),
    onPrimaryContainer = Color(0xFFDDE3E6),
    secondary = Color(0xFF90A4AE),
    onSecondary = Color(0xFF15191C),
    secondaryContainer = Color(0xFF2E353A),
    onSecondaryContainer = Color(0xFFDDE3E6),
    tertiary = Color(0xFF78909C),
    onTertiary = Color(0xFF15191C),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE3E6E8),
    surface = Color(0xFF1C1F21),
    onSurface = Color(0xFFE3E6E8),
    surfaceVariant = Color(0xFF2A2E31),
    onSurfaceVariant = Color(0xFFC5CCD1),
    outline = Color(0xFF8C969D),
    outlineVariant = Color(0xFF3A4147),
)

@Composable
fun PhoneKeyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) MetalDarkColorScheme else MetalLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}