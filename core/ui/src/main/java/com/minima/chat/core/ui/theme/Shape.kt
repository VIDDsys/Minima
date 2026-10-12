package com.minima.chat.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object MinimaDimens {
    val ComposerRadius = 20.dp
    val ComposerRadiusExpanded = 16.dp
    val ChipRadius = 16.dp
    val BubbleRadius = 16.dp
    val SettingsGroupRadius = 12.dp
    val ScreenPadding = 16.dp
    val ComposerOuterPadding = 16.dp
    val MessageSpacing = 8.dp
    val ActionButtonSize = 36.dp
    val ComposerMinHeight = 48.dp
}

val MinimaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
