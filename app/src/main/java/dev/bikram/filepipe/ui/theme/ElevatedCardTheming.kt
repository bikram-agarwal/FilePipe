package dev.bikram.filepipe.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Elevated card colors used by every list card so all cards read the same as
 * Settings and edit surfaces. Uses the theme's surfaceContainer directly; the
 * enhanced-shading toggle is handled upstream in [FilePipeTheme] by gating
 * primary surface boost on the color scheme (same split as Remember's Theme /
 * elevatedCardColors).
 */
@Composable
fun elevatedCardColors(): CardColors {
    val scheme = MaterialTheme.colorScheme
    val darkUi = ColorUtils.calculateLuminance(scheme.background.toArgb()) < 0.35
    val contentColor = if (darkUi) Color(0xFFE6E6EA) else Color(0xFF1C1B1F)
    return CardDefaults.elevatedCardColors(
        containerColor = scheme.surfaceContainer,
        contentColor = contentColor,
    )
}
