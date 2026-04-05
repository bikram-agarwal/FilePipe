package dev.bikram.filepipe.data.preferences

import androidx.compose.ui.graphics.Color

enum class AppColorSource {
    DEFAULT,
    MATERIAL_YOU,
    /** Seed from [AppPreferences.activeCustomSeedHex] (Material Kolor, same as presets). */
    CUSTOM,
    PRESET_SAPPHIRE,
    PRESET_EMERALD,
    PRESET_AMBER,
    PRESET_VIOLET,
    PRESET_CORAL,
    PRESET_TEAL,
    PRESET_LIME,
    PRESET_ROSE,
    PRESET_SLATE;

    val isSeedBased: Boolean
        get() = when (this) {
            CUSTOM,
            PRESET_SAPPHIRE, PRESET_EMERALD, PRESET_AMBER, PRESET_VIOLET, PRESET_CORAL,
            PRESET_TEAL, PRESET_LIME, PRESET_ROSE, PRESET_SLATE -> true
            else -> false
        }

    fun seedPrimary(): Color? = when (this) {
        PRESET_SAPPHIRE -> Color(0xFF1565C0)
        PRESET_EMERALD -> Color(0xFF2E7D32)
        PRESET_AMBER -> Color(0xFFFF8F00)
        PRESET_VIOLET -> Color(0xFF7B1FA2)
        PRESET_CORAL -> Color(0xFFE53935)
        PRESET_TEAL -> Color(0xFF00796B)
        PRESET_LIME -> Color(0xFFAFB42B)
        PRESET_ROSE -> Color(0xFFE91E63)
        PRESET_SLATE -> Color(0xFF546E7A)
        else -> null
    }

    companion object {
        val accentOptions: List<AppColorSource> = listOf(
            DEFAULT,
            MATERIAL_YOU,
            PRESET_SAPPHIRE,
            PRESET_EMERALD,
            PRESET_AMBER,
            PRESET_VIOLET,
            PRESET_CORAL,
            PRESET_TEAL,
            PRESET_LIME,
            PRESET_ROSE,
            PRESET_SLATE
        )
    }
}
