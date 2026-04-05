package dev.bikram.filepipe.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import android.graphics.Color as AndroidGraphicsColor

/** Measured from pre-boost list cards; used as the grey anchor before a light surface hint. */
private val fixedDarkCardNeutralBase = Color(0xFF1E2426)

private val fixedLightCardNeutralBase = Color(0xFFF1F3F5)

/** How much [ColorScheme.surfaceContainer] is mixed in so the card picks up a faint theme hue. */
private const val fixedCardSurfaceHintBlendRatio = 0.14f

/** Nudge dark fixed cards slightly below the tinted mix (keeps hue, drops luminance a hair). */
private const val fixedDarkCardFinalDarkenRatio = 0.032f

/**
 * Elevated card colors for list cards and grouped settings: either the current dynamic scheme
 * (accent-tinted when the theme boosts surfaces) or near-neutral surfaces with a low-saturation
 * hint from [ColorScheme.surfaceContainer].
 */
@Composable
fun elevatedCardColors(): CardColors {
    if (!LocalUseFixedCardColors.current) {
        return CardDefaults.elevatedCardColors()
    }
    val scheme = MaterialTheme.colorScheme
    val isDarkUi = ColorUtils.calculateLuminance(scheme.background.toArgb()) < 0.35f
    return if (isDarkUi) {
        val tintedDarkCard = ColorUtils.blendARGB(
            fixedDarkCardNeutralBase.toArgb(),
            scheme.surfaceContainer.toArgb(),
            fixedCardSurfaceHintBlendRatio
        )
        val containerColor = Color(
            ColorUtils.blendARGB(
                tintedDarkCard,
                AndroidGraphicsColor.BLACK,
                fixedDarkCardFinalDarkenRatio
            )
        )
        CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = Color(0xFFE6E6EA)
        )
    } else {
        val containerColor = Color(
            ColorUtils.blendARGB(
                fixedLightCardNeutralBase.toArgb(),
                scheme.surfaceContainer.toArgb(),
                fixedCardSurfaceHintBlendRatio
            )
        )
        CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = Color(0xFF1C1B1F)
        )
    }
}
