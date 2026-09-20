@file:Suppress("ConfigurationScreenWidthHeight")

package dev.bikram.filepipe.ui.common

import android.content.res.Configuration
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Single source of truth for the app's responsive breakpoints. Screens used to each re-derive
 * "is this a short landscape window?" inline from [LocalConfiguration], duplicating the same
 * orientation + height check (and the magic 480 threshold). Everything now routes through the
 * helpers below so the threshold and measurement stay consistent.
 */

/** Below this many dp of height in landscape, screens switch to space-saving layouts. */
const val SMALL_LANDSCAPE_HEIGHT_DP = 480

/**
 * Material's Expanded-width lower bound, cross-checked against the OS-reported window width so a
 * replaced Compose density can never promote a narrow window to tablet chrome. Deliberately keyed on
 * the current window width rather than `smallestScreenWidthDp`: the latter is the smaller dimension
 * across both orientations, so a phone would never qualify even in a genuinely wide landscape window.
 */
const val MIN_MULTI_PANE_WIDTH_DP = 840

/**
 * Adaptive information captured before [FilePipeTheme][dev.bikram.filepipe.ui.theme.FilePipeTheme]
 * replaces [androidx.compose.ui.platform.LocalDensity] for visual scaling.
 */
val LocalSystemWindowAdaptiveInfo = staticCompositionLocalOf<WindowAdaptiveInfo?> { null }

/** The pane directive calculated from [LocalSystemWindowAdaptiveInfo]. */
val LocalSystemPaneScaffoldDirective = staticCompositionLocalOf<PaneScaffoldDirective?> { null }

fun supportsMultiPaneLayout(
    maxHorizontalPartitions: Int,
    screenWidthDp: Int,
): Boolean =
    maxHorizontalPartitions > 1 &&
        screenWidthDp >= MIN_MULTI_PANE_WIDTH_DP

/**
 * Lets a container opt its subtree out of the compact control sizes that [isSmallLandscape] would
 * otherwise trigger, for the cases where the container knows it has the room. The bottom chrome
 * strip does this: it is a single row of controls over the content, so full-size buttons cost it
 * nothing, whereas a screen full of list rows genuinely needs the height back.
 *
 * Read it alongside [isSmallLandscape] rather than instead of it - compact still requires a short
 * landscape window, this only vetoes it.
 */
val LocalAllowCompactControls = staticCompositionLocalOf { true }

enum class ResponsiveActionLayout {
    HORIZONTAL,
    STACKED,
}

fun responsiveTextScaleForWidth(availableWidth: Dp): Float =
    when {
        availableWidth < 320.dp -> 0.84f
        availableWidth < 360.dp -> 0.88f
        availableWidth < 430.dp -> 0.93f
        else -> 1f
    }

fun responsiveActionLayout(
    availableWidth: Dp,
    effectiveFontScale: Float,
    itemCount: Int,
): ResponsiveActionLayout {
    if (itemCount <= 1) return ResponsiveActionLayout.HORIZONTAL
    val tooNarrowForRow =
        availableWidth < 360.dp ||
            (availableWidth < 430.dp && effectiveFontScale > 1.10f) ||
            (availableWidth < 520.dp && effectiveFontScale > 1.15f)
    return if (tooNarrowForRow) ResponsiveActionLayout.STACKED else ResponsiveActionLayout.HORIZONTAL
}

@Composable
@ReadOnlyComposable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * True in landscape on a short window (e.g. most phones rotated, or a small split-screen pane),
 * where screens drop to compact spacing / smaller controls / hide non-essential chrome.
 */
@Composable
@ReadOnlyComposable
fun isSmallLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenHeightDp < SMALL_LANDSCAPE_HEIGHT_DP
}
