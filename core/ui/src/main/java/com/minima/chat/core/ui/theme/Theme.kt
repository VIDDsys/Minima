package com.minima.chat.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalMinimaColors = staticCompositionLocalOf { lightMinimaColors() }

enum class AppearanceMode { System, Light, Dark }

@Composable
fun MinimaTheme(
    appearance: AppearanceMode = AppearanceMode.System,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance) {
        AppearanceMode.System -> systemDark
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }
    val minima = if (dark) darkMinimaColors() else lightMinimaColors()
    val m3 = if (dark) {
        darkColorScheme(
            primary = minima.primary,
            onPrimary = minima.onPrimary,
            primaryContainer = minima.bgElevated,
            onPrimaryContainer = minima.text,
            secondary = minima.accent,
            onSecondary = Color.White,
            background = minima.bgLayout,
            onBackground = minima.text,
            surface = minima.bgLayout,
            onSurface = minima.text,
            surfaceVariant = minima.bgContainer,
            onSurfaceVariant = minima.textSecondary,
            outline = minima.border,
            error = minima.error,
        )
    } else {
        lightColorScheme(
            primary = minima.primary,
            onPrimary = minima.onPrimary,
            primaryContainer = minima.bgContainer,
            onPrimaryContainer = minima.text,
            secondary = minima.accent,
            onSecondary = Color.White,
            background = minima.bgLayout,
            onBackground = minima.text,
            surface = minima.bgLayout,
            onSurface = minima.text,
            surfaceVariant = minima.bgContainer,
            onSurfaceVariant = minima.textSecondary,
            outline = minima.border,
            error = minima.error,
        )
    }
    CompositionLocalProvider(LocalMinimaColors provides minima) {
        MaterialTheme(
            colorScheme = m3,
            typography = MinimaTypography,
            shapes = MinimaShapes,
            content = content,
        )
    }
}

object MinimaThemeAccess {
    val colors: MinimaColorScheme
        @Composable get() = LocalMinimaColors.current
}
