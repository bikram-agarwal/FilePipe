package dev.bikram.filepipe.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeDisplayDensityTest {
    @Test
    fun largeSystemDisplaySizeIsCappedAgainstStableDensity() {
        assertEquals(3.45f, appDisplayDensity(systemDensity = 4.0f, stableDensity = 3.0f), 0.001f)
        assertEquals(1.15f, appDisplayDensity(systemDensity = 1.5f, stableDensity = 1.0f), 0.001f)
    }

    @Test
    fun defaultOrSmallerSystemDisplaySizeIsUnchanged() {
        assertEquals(3.0f, appDisplayDensity(systemDensity = 3.0f, stableDensity = 3.0f), 0.001f)
        assertEquals(2.55f, appDisplayDensity(systemDensity = 2.55f, stableDensity = 3.0f), 0.001f)
    }

    @Test
    fun unsetStableDensityFallbackDoesNotShrinkTheApp() {
        assertEquals(2.55f, appDisplayDensity(systemDensity = 2.55f, stableDensity = 1.0f), 0.001f)
        assertEquals(3.0f, appDisplayDensity(systemDensity = 3.0f, stableDensity = 1.0f), 0.001f)
    }
}
