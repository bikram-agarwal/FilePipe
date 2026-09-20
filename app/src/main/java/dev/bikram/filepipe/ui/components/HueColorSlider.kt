package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.core.graphics.toColorInt
import dev.bikram.filepipe.ui.theme.normalizeSeedHexOrNull
import java.util.Locale
import android.graphics.Color as AndroidColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueColorSlider(
    selectedHex: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    fallbackHue: Float = 270f,
    sliderPanelColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onValueChangeFinished: ((String) -> Unit)? = null,
) {
    val fallbackHex = colorHexFromHue(fallbackHue)
    val normalizedHex = normalizeSeedHexOrNull(selectedHex.orEmpty()) ?: fallbackHex
    var hue by rememberSaveable {
        mutableFloatStateOf(hueFromHexColor(normalizedHex) ?: fallbackHue)
    }
    var currentHex by rememberSaveable { mutableStateOf(normalizedHex) }

    LaunchedEffect(normalizedHex) {
        if (normalizedHex != currentHex) {
            currentHex = normalizedHex
            hue = hueFromHexColor(normalizedHex) ?: fallbackHue
        }
    }

    val thumbColor = colorFromHue(hue, saturation = HUE_SLIDER_THUMB_SATURATION, value = HUE_SLIDER_THUMB_VALUE)
    val lightPanel = sliderPanelColor.luminance() > 0.5f
    val handleGapWidth = if (lightPanel) HueSliderLightHandleGapWidth else HueSliderHandleGapWidth
    val handleWidth = if (lightPanel) HueSliderLightHandleWidth else HueSliderHandleWidth
    val sliderColors =
        SliderDefaults.colors(
            thumbColor = Color.Transparent,
            activeTrackColor = Color.Transparent,
            activeTickColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        )
    val trackColors =
        SliderDefaults.colors(
            thumbColor = Color.Transparent,
            activeTrackColor = Color.Transparent,
            activeTickColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        )
    val sliderState =
        rememberSliderState(
            value = hue,
            trackRange = 0f..360f,
        )

    LaunchedEffect(hue) {
        if (sliderState.value != hue) {
            sliderState.value = hue
        }
    }

    Slider(
        state = sliderState,
        modifier = modifier.fillMaxWidth(),
        onValueChange = { nextHue: Float ->
            hue = nextHue
            currentHex = colorHexFromHue(nextHue)
            onSelect(currentHex)
        },
        onValueChangeFinished = { onValueChangeFinished?.invoke(currentHex) },
        colors = sliderColors,
        track = { sliderState: SliderState ->
            val trackHue = sliderState.value
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(HueSliderHandleHeight),
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .height(HueSliderTrackHeight)
                            .clip(HueSliderTrackShape)
                            .background(Brush.horizontalGradient(HueSliderColors)),
                )
                val handleGapOffset = maxWidth * (trackHue / 360f) - handleGapWidth / 2
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset(handleGapOffset.roundToPx(), 0) }
                            .size(width = handleGapWidth, height = HueSliderTrackHeight)
                            .background(sliderPanelColor),
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset((maxWidth * (trackHue / 360f) - handleWidth / 2).roundToPx(), 0) }
                            .size(width = handleWidth, height = HueSliderHandleHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(thumbColor),
                )
                SliderDefaults.Track(
                    sliderState = sliderState,
                    trackCornerSize = HueSliderTrackCorner,
                    colors = trackColors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = HueSliderThumbTrackGap,
                    trackInsideCornerSize = HueSliderInsideCorner,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

fun hueFromHexColor(hex: String): Float? {
    val normalized = normalizeSeedHexOrNull(hex) ?: return null
    val hsv = FloatArray(3)
    return runCatching {
        AndroidColor.colorToHSV(normalized.toColorInt(), hsv)
        hsv[0]
    }.getOrNull()
}

fun colorHexFromHue(hue: Float): String {
    val colorInt = androidColorFromHue(hue, HUE_SLIDER_GENERATED_SATURATION, HUE_SLIDER_GENERATED_VALUE)
    return String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)
}

private fun colorFromHue(
    hue: Float,
    saturation: Float,
    value: Float,
): Color = Color(androidColorFromHue(hue, saturation, value))

private fun androidColorFromHue(
    hue: Float,
    saturation: Float,
    value: Float,
): Int =
    AndroidColor.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, 360f),
            saturation,
            value,
        ),
    )

fun colorFromHexOrDefault(
    hex: String,
    fallbackHue: Float = 270f,
): Color {
    val colorInt =
        runCatching { hex.toColorInt() }
            .getOrDefault(
                androidColorFromHue(fallbackHue, HUE_SLIDER_GENERATED_SATURATION, HUE_SLIDER_GENERATED_VALUE),
            )
    return Color(colorInt)
}

private val HueSliderColors =
    listOf(
        Color(0xFFE95A50),
        Color(0xFFE8B84E),
        Color(0xFFD5DB4C),
        Color(0xFF58D95C),
        Color(0xFF43CDD0),
        Color(0xFF5569E8),
        Color(0xFFD64BDD),
        Color(0xFFE95A50),
    )

private const val HUE_SLIDER_GENERATED_SATURATION = 0.66f
private const val HUE_SLIDER_GENERATED_VALUE = 0.90f
private const val HUE_SLIDER_THUMB_SATURATION = 0.58f
private const val HUE_SLIDER_THUMB_VALUE = 0.86f
private val HueSliderTrackHeight = 28.dp
private val HueSliderTrackCorner = 10.dp
private val HueSliderTrackShape = RoundedCornerShape(HueSliderTrackCorner)
private val HueSliderInsideCorner = 3.dp
private val HueSliderThumbTrackGap = 4.dp
private val HueSliderHandleGapWidth = 14.dp
private val HueSliderHandleWidth = 5.dp
private val HueSliderLightHandleGapWidth = 13.dp
private val HueSliderLightHandleWidth = 6.dp
private val HueSliderHandleHeight = 42.dp
