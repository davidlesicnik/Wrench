package com.lesicnik.wrench.ui.theme

import android.os.Build
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Orange80,
    primaryContainer = Color(0xFF123B66),
    onPrimaryContainer = Color(0xFFD6E7FF),
    secondaryContainer = Color(0xFF293B50),
    onSecondaryContainer = Color(0xFFD8E5F5),
    background = Color(0xFF101419),
    surface = Color(0xFF101419),
    surfaceContainerLowest = Color(0xFF0B0F14),
    surfaceContainerLow = Color(0xFF181D24),
    surfaceContainer = Color(0xFF1C222A),
    surfaceContainerHigh = Color(0xFF272E37),
    surfaceContainerHighest = Color(0xFF323A45)
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Orange40,
    primaryContainer = Color(0xFFD8E7FF),
    onPrimaryContainer = Color(0xFF082F59),
    secondaryContainer = Color(0xFFE1E9F3),
    onSecondaryContainer = Color(0xFF263C50),
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFF8F9FC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainer = Color(0xFFEBEFF5),
    surfaceContainerHigh = Color(0xFFE5EAF1),
    surfaceContainerHighest = Color(0xFFDFE5ED)
)

@Composable
fun WrenchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
