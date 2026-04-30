package com.scribe.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Teal200 = Color(0xFF03DAC5)
val Teal700 = Color(0xFF018786)
val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Purple700 = Color(0xFF3700B3)
val SurfaceDark = Color(0xFF1A1A2E)
val BackgroundDark = Color(0xFF0F0F23)
val SurfaceLight = Color(0xFFF5F5F5)
val BackgroundLight = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = Teal200,
    secondary = Purple200,
    surface = SurfaceDark,
    background = BackgroundDark,
    onSurface = Color.White,
    onBackground = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Teal700,
    secondary = Purple500,
    surface = SurfaceLight,
    background = BackgroundLight,
    onSurface = Color.Black,
    onBackground = Color.Black
)

@Composable
fun ScribeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
