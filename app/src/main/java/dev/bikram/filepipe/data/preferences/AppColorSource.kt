package dev.bikram.filepipe.data.preferences

import androidx.compose.ui.graphics.Color

data class CuratedColorTriplet(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

@Suppress("DEPRECATION")
enum class AppColorSource {
    DEFAULT,
    MATERIAL_YOU,

    /** Seed from [AppPreferences.activeCustomSeedHex] (Material Kolor, same as presets). */
    CUSTOM,

    CURATED_EMBER,
    CURATED_GROVE,
    CURATED_HONEY,
    CURATED_OCEAN,
    CURATED_IRIS,
    CURATED_DUSK,
    CURATED_BERRY,

    /** Legacy single-seed presets kept for backup/DataStore compatibility. */
    @Deprecated("Migrated to CURATED_OCEAN")
    PRESET_SAPPHIRE,

    @Deprecated("Migrated to DEFAULT")
    PRESET_EMERALD,

    @Deprecated("Migrated to CURATED_GROVE")
    PRESET_AMBER,

    @Deprecated("Migrated to CURATED_DUSK")
    PRESET_VIOLET,

    @Deprecated("Migrated to CURATED_EMBER")
    PRESET_CORAL,

    @Deprecated("Migrated to CURATED_OCEAN")
    PRESET_TEAL,

    @Deprecated("Migrated to DEFAULT")
    PRESET_LIME,

    @Deprecated("Migrated to CURATED_BERRY")
    PRESET_ROSE,

    @Deprecated("Migrated to DEFAULT")
    PRESET_SLATE,
    ;

    val isSeedBased: Boolean
        get() =
            when (this) {
                CUSTOM,
                CURATED_EMBER,
                CURATED_GROVE,
                CURATED_HONEY,
                CURATED_OCEAN,
                CURATED_IRIS,
                CURATED_DUSK,
                CURATED_BERRY,
                PRESET_SAPPHIRE,
                PRESET_EMERALD,
                PRESET_AMBER,
                PRESET_VIOLET,
                PRESET_CORAL,
                PRESET_TEAL,
                PRESET_LIME,
                PRESET_ROSE,
                PRESET_SLATE,
                -> true

                else -> false
            }

    val supportsPaletteStyle: Boolean
        get() = this != MATERIAL_YOU

    fun curatedTriplet(): CuratedColorTriplet? =
        when (this.migrated()) {
            DEFAULT -> generateTripletForSeed(Color(0xFF16A34A))
            CURATED_EMBER -> generateTripletForSeed(Color(0xFFF97316))
            CURATED_GROVE -> generateTripletForSeed(Color(0xFF6B8E23))
            CURATED_HONEY -> generateTripletForSeed(Color(0xFFFACC15))
            CURATED_OCEAN -> generateTripletForSeed(Color(0xFF0284C7))
            CURATED_IRIS -> generateTripletForSeed(Color(0xFF7C3AED))
            CURATED_DUSK -> generateTripletForSeed(Color(0xFF6B7280))
            CURATED_BERRY -> generateTripletForSeed(Color(0xFFD946EF))
            else -> null
        }

    fun seedPrimary(): Color? =
        curatedTriplet()?.primary ?: when (this) {
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

    @Suppress("DEPRECATION")
    fun migrated(): AppColorSource =
        when (this) {
            PRESET_SAPPHIRE, PRESET_TEAL -> CURATED_OCEAN
            PRESET_EMERALD, PRESET_LIME, PRESET_SLATE -> DEFAULT
            PRESET_AMBER -> CURATED_GROVE
            PRESET_VIOLET -> CURATED_DUSK
            PRESET_CORAL -> CURATED_EMBER
            PRESET_ROSE -> CURATED_BERRY
            else -> this
        }

    companion object {
        val accentOptions: List<AppColorSource> =
            listOf(
                MATERIAL_YOU,
                DEFAULT,
                CURATED_EMBER,
                CURATED_GROVE,
                CURATED_HONEY,
                CURATED_OCEAN,
                CURATED_IRIS,
                CURATED_DUSK,
                CURATED_BERRY,
            )
    }
}

fun colorToHsl(
    color: Color,
    hslArray: FloatArray,
) {
    val redVal = color.red
    val greenVal = color.green
    val blueVal = color.blue

    val maxVal = maxOf(redVal, maxOf(greenVal, blueVal))
    val minVal = minOf(redVal, minOf(greenVal, blueVal))
    val delta = maxVal - minVal

    var hueVal = 0f
    var saturationVal = 0f
    val lightnessVal = (maxVal + minVal) / 2f

    if (delta != 0f) {
        saturationVal =
            if (lightnessVal < 0.5f) {
                delta / (maxVal + minVal)
            } else {
                delta / (2f - maxVal - minVal)
            }
        hueVal =
            when (maxVal) {
                redVal -> (greenVal - blueVal) / delta + (if (greenVal < blueVal) 6f else 0f)
                greenVal -> (blueVal - redVal) / delta + 2f
                else -> (redVal - greenVal) / delta + 4f
            }
        hueVal *= 60f
    }

    hslArray[0] = hueVal
    hslArray[1] = saturationVal
    hslArray[2] = lightnessVal
}

fun hslToColor(
    hueVal: Float,
    saturationVal: Float,
    lightnessVal: Float,
): Color {
    val chroma = (1f - kotlin.math.abs(2f * lightnessVal - 1f)) * saturationVal
    val xValue = chroma * (1f - kotlin.math.abs((hueVal / 60f) % 2f - 1f))
    val matchValue = lightnessVal - chroma / 2f

    val (redValue, greenValue, blueValue) =
        when {
            hueVal < 60f -> Triple(chroma, xValue, 0f)
            hueVal < 120f -> Triple(xValue, chroma, 0f)
            hueVal < 180f -> Triple(0f, chroma, xValue)
            hueVal < 240f -> Triple(0f, xValue, chroma)
            hueVal < 300f -> Triple(xValue, 0f, chroma)
            else -> Triple(chroma, 0f, xValue)
        }

    return Color(
        red = redValue + matchValue,
        green = greenValue + matchValue,
        blue = blueValue + matchValue,
        alpha = 1f,
    )
}

fun generateTripletForSeed(primaryColor: Color): CuratedColorTriplet {
    val hslArray = FloatArray(3)
    colorToHsl(primaryColor, hslArray)
    val hueVal = hslArray[0]
    val saturationVal = hslArray[1]
    val lightnessVal = hslArray[2]

    // Secondary: shift hue +20, soft but clear saturation (e.g. 70% of primary, min 35%), slightly adjusted lightness
    val secHue = (hueVal + 20f) % 360f
    val secSaturation = (saturationVal * 0.70f).coerceIn(0.35f, 0.85f)
    val finalSecSaturation = if (saturationVal < 0.30f) saturationVal else secSaturation
    val secLightness =
        if (lightnessVal < 0.5f) {
            (lightnessVal + 0.04f).coerceIn(0f, 1f)
        } else {
            (lightnessVal - 0.04f).coerceIn(0f, 1f)
        }
    val secondaryColor = hslToColor(secHue, finalSecSaturation, secLightness)

    // Tertiary: shift hue -40, vibrant saturation (e.g. 85% of primary, min 40%), slightly adjusted lightness
    val terHue = (hueVal - 40f + 360f) % 360f
    val terSaturation = (saturationVal * 0.85f).coerceIn(0.40f, 0.90f)
    val finalTerSaturation = if (saturationVal < 0.35f) saturationVal else terSaturation
    val terLightness =
        if (lightnessVal < 0.5f) {
            (lightnessVal + 0.08f).coerceIn(0f, 1f)
        } else {
            (lightnessVal - 0.08f).coerceIn(0f, 1f)
        }
    val tertiaryColor = hslToColor(terHue, finalTerSaturation, terLightness)

    return CuratedColorTriplet(primaryColor, secondaryColor, tertiaryColor)
}
