package dev.bikram.filepipe.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeUiScaleTest {
    @Test
    fun clampUiScaleUsesObtainXRangeAndDefault() {
        assertEquals(DEFAULT_UI_SCALE, clampUiScale(Float.NaN))
        assertEquals(DEFAULT_UI_SCALE, clampUiScale(0f))
        assertEquals(DEFAULT_UI_SCALE, clampUiScale(-1f))
        assertEquals(UI_SCALE_MIN, clampUiScale(0.5f))
        assertEquals(UI_SCALE_MAX, clampUiScale(2f))
        assertEquals(1.1f, clampUiScale(1.1f))
    }
}
