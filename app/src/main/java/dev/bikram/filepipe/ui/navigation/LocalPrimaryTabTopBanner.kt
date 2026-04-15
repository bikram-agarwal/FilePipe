package dev.bikram.filepipe.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Optional update / promo strip rendered at the top of primary tab [Scaffold] headers,
 * above [androidx.compose.material3.LargeTopAppBar], so it shares the same transparent header chrome.
 */
val LocalPrimaryTabTopBanner = compositionLocalOf<@Composable () -> Unit> {
    { }
}

/** When true, [LocalPrimaryTabTopBanner] is showing so [LargeTopAppBar] should not re-apply top status insets. */
val LocalPrimaryTabTopBannerActive = compositionLocalOf { false }
