package com.drnishanth.novellib.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = Color.Black,
    onBackground = TextDark,
    onSurface = TextDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = OnPrimaryLight,
    onBackground = TextLight,
    onSurface = TextLight
)

private val SepiaColorScheme = lightColorScheme(
    primary = TextSepia,
    background = BackgroundSepia,
    surface = SurfaceSepia,
    onPrimary = Color.White,
    onBackground = TextSepia,
    onSurface = TextSepia
)

private val EInkColorScheme = lightColorScheme(
    primary = Color.Black,
    background = BackgroundEInk,
    surface = SurfaceEInk,
    onPrimary = Color.White,
    onBackground = TextEInk,
    onSurface = TextEInk
)

@Composable
fun NovelLibTheme(
    themeName: String = "system", // system, light, dark, sepia, eink
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeName.lowercase()) {
        "dark" -> DarkColorScheme
        "sepia" -> SepiaColorScheme
        "eink" -> EInkColorScheme
        "light" -> LightColorScheme
        else -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
