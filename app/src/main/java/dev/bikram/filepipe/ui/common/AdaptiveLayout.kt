package dev.bikram.filepipe.ui.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/*
 * Single source of truth for the app's responsive breakpoints. Screens used to each re-derive
 * "is this a short landscape window?" inline from [LocalConfiguration], duplicating the same
 * orientation + height check (and the magic 480 threshold). Everything now routes through the
 * helpers below so the threshold and measurement stay consistent.
 */

/** Below this many dp of height in landscape, screens switch to space-saving layouts. */
const val SMALL_LANDSCAPE_HEIGHT_DP = 480

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
