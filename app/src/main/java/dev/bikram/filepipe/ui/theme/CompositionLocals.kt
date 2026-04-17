package dev.bikram.filepipe.ui.theme

import androidx.compose.runtime.compositionLocalOf

/** When true, main tab [androidx.compose.material3.Scaffold]s use a transparent container so the root gradient shows through. */
val LocalUseGradientBackground = compositionLocalOf { false }

/** When true, root chrome may apply progressive edge blur; inner screens can match with transparent app bars. */
val LocalProgressiveBlurEnabled = compositionLocalOf { true }

/**
 * Edge blur parameters for the current route, or null when progressive blur is off.
 * Apply via [dev.bikram.filepipe.ui.modifiers.applyToScrollableList] or [dev.bikram.filepipe.ui.modifiers.applyToFullBleedLayer] so app bars stay sharp.
 */
data class ProgressiveBlurStyle(
    /** Top fade/blur band in px (local to the blurred composable; match app bar + status insets). */
    val topHeightPx: Float,
    val bottomHeightPx: Float,
    val blurRadius: Float,
    /** Gradient overlay strength at the top edge. */
    val overlayAlpha: Float,
    /** Gradient overlay strength at the bottom edge (may exceed [overlayAlpha] for stronger bottom scrim). */
    val overlayAlphaBottom: Float
)

val LocalProgressiveBlurStyle = compositionLocalOf<ProgressiveBlurStyle?> { null }
