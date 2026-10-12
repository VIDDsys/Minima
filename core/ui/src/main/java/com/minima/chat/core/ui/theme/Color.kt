package com.minima.chat.core.ui.theme

import androidx.compose.ui.graphics.Color

/** Minima original palette — near-monochrome with warm charcoal ink + sparse teal accent. */
object MinimaColors {
    // Light
    val LightBgLayout = Color(0xFFFDFDFD)
    val LightBgBase = Color(0xFFFFFFFF)
    val LightBgContainer = Color(0xFFF6F6F6)
    val LightBgElevated = Color(0xFFF9F9F9)
    val LightText = Color(0xFF1A1A1A)
    val LightTextSecondary = Color(0xFF636261)
    val LightTextTertiary = Color(0xFF838280)
    val LightTextQuaternary = Color(0xFF9F9E9B)
    val LightPrimary = Color(0xFF1A1A1A)
    val LightOnPrimary = Color(0xFFFDFDFD)
    val LightBorder = Color(0xFFBCBBBA)
    val LightBorderSecondary = Color(0xFFDDDDDD)
    val LightFill = Color(0xFFDFDFDF)
    val LightFillTertiary = Color(0xFFF0F0F0)
    val LightError = Color(0xFFFF4D4F)
    val LightAccent = Color(0xFF0D9488)

    // Dark
    val DarkBgLayout = Color(0xFF161612)
    val DarkBgBase = Color(0xFF0F0D0C)
    val DarkBgContainer = Color(0xFF0D0D0C)
    val DarkBgElevated = Color(0xFF1A1A17)
    val DarkText = Color(0xFFE8E8E5)
    val DarkTextSecondary = Color(0xFF95958F)
    val DarkTextTertiary = Color(0xFF7B7B75)
    val DarkTextQuaternary = Color(0xFF484743)
    val DarkPrimary = Color(0xFFFDFDFD)
    val DarkOnPrimary = Color(0xFF171716)
    val DarkBorder = Color(0xFF61615B)
    val DarkBorderSecondary = Color(0xFF484843)
    val DarkFill = Color(0xFF31312D)
    val DarkFillTertiary = Color(0xFF1D1D1A)
    val DarkError = Color(0xFFEC5B56)
    val DarkAccent = Color(0xFF14B8A6)
}

data class MinimaColorScheme(
    val bgLayout: Color,
    val bgBase: Color,
    val bgContainer: Color,
    val bgElevated: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    val primary: Color,
    val onPrimary: Color,
    val border: Color,
    val borderSecondary: Color,
    val fill: Color,
    val fillTertiary: Color,
    val error: Color,
    val accent: Color,
    val userBubble: Color,
    val assistantBubble: Color,
)

fun lightMinimaColors() = MinimaColorScheme(
    bgLayout = MinimaColors.LightBgLayout,
    bgBase = MinimaColors.LightBgBase,
    bgContainer = MinimaColors.LightBgContainer,
    bgElevated = MinimaColors.LightBgElevated,
    text = MinimaColors.LightText,
    textSecondary = MinimaColors.LightTextSecondary,
    textTertiary = MinimaColors.LightTextTertiary,
    textQuaternary = MinimaColors.LightTextQuaternary,
    primary = MinimaColors.LightPrimary,
    onPrimary = MinimaColors.LightOnPrimary,
    border = MinimaColors.LightBorder,
    borderSecondary = MinimaColors.LightBorderSecondary,
    fill = MinimaColors.LightFill,
    fillTertiary = MinimaColors.LightFillTertiary,
    error = MinimaColors.LightError,
    accent = MinimaColors.LightAccent,
    userBubble = MinimaColors.LightPrimary,
    assistantBubble = MinimaColors.LightBgContainer,
)

fun darkMinimaColors() = MinimaColorScheme(
    bgLayout = MinimaColors.DarkBgLayout,
    bgBase = MinimaColors.DarkBgBase,
    bgContainer = MinimaColors.DarkBgContainer,
    bgElevated = MinimaColors.DarkBgElevated,
    text = MinimaColors.DarkText,
    textSecondary = MinimaColors.DarkTextSecondary,
    textTertiary = MinimaColors.DarkTextTertiary,
    textQuaternary = MinimaColors.DarkTextQuaternary,
    primary = MinimaColors.DarkPrimary,
    onPrimary = MinimaColors.DarkOnPrimary,
    border = MinimaColors.DarkBorder,
    borderSecondary = MinimaColors.DarkBorderSecondary,
    fill = MinimaColors.DarkFill,
    fillTertiary = MinimaColors.DarkFillTertiary,
    error = MinimaColors.DarkError,
    accent = MinimaColors.DarkAccent,
    userBubble = MinimaColors.DarkPrimary,
    assistantBubble = MinimaColors.DarkBgElevated,
)
