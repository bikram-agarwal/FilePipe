package dev.bikram.filepipe.ui.common

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun responsiveTextScaleForWidthUsesSharedBreakpoints() {
        assertEquals(0.84f, responsiveTextScaleForWidth(319.dp))
        assertEquals(0.88f, responsiveTextScaleForWidth(320.dp))
        assertEquals(0.88f, responsiveTextScaleForWidth(359.dp))
        assertEquals(0.93f, responsiveTextScaleForWidth(360.dp))
        assertEquals(0.93f, responsiveTextScaleForWidth(429.dp))
        assertEquals(1f, responsiveTextScaleForWidth(430.dp))
    }

    @Test
    fun responsiveActionLayoutStacksOnlyWhenControlsAreLikelyCramped() {
        assertEquals(
            ResponsiveActionLayout.HORIZONTAL,
            responsiveActionLayout(520.dp, effectiveFontScale = 1.20f, itemCount = 2),
        )
        assertEquals(
            ResponsiveActionLayout.STACKED,
            responsiveActionLayout(519.dp, effectiveFontScale = 1.20f, itemCount = 2),
        )
        assertEquals(
            ResponsiveActionLayout.STACKED,
            responsiveActionLayout(359.dp, effectiveFontScale = 1.00f, itemCount = 2),
        )
        assertEquals(
            ResponsiveActionLayout.HORIZONTAL,
            responsiveActionLayout(320.dp, effectiveFontScale = 1.20f, itemCount = 1),
        )
    }

    @Test
    fun multiPaneLayoutRequiresBothExpandedDirectiveAndExpandedWindowWidth() {
        assertEquals(false, supportsMultiPaneLayout(maxHorizontalPartitions = 2, screenWidthDp = 438))
        assertEquals(false, supportsMultiPaneLayout(maxHorizontalPartitions = 1, screenWidthDp = 840))
        assertEquals(true, supportsMultiPaneLayout(maxHorizontalPartitions = 2, screenWidthDp = 840))
        // Phone in landscape at the smallest system display size: wide window, short side stays phone-sized.
        assertEquals(true, supportsMultiPaneLayout(maxHorizontalPartitions = 2, screenWidthDp = 1079))
    }
}
