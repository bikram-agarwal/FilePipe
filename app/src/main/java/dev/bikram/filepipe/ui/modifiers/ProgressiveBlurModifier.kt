package dev.bikram.filepipe.ui.modifiers

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import dev.bikram.filepipe.ui.theme.ProgressiveBlurStyle

enum class BlurDirection {
    TOP,
    BOTTOM
}

private val dualEdgeBlurAgsl = """
    uniform shader content;
    uniform float blurRadius;
    uniform float topHeight;
    uniform float bottomHeight;
    uniform float contentHeight;

    half4 main(float2 fragCoord) {
        float topProgress = topHeight > 0.0
            ? 1.0 - clamp(fragCoord.y / topHeight, 0.0, 1.0)
            : 0.0;
        float bottomProgress = bottomHeight > 0.0
            ? 1.0 - clamp((contentHeight - fragCoord.y) / bottomHeight, 0.0, 1.0)
            : 0.0;

        float progress = pow(max(topProgress, bottomProgress), 1.5);
        float radius = progress * blurRadius;

        if (radius <= 0.0) {
            return content.eval(fragCoord);
        }

        half4 accum = half4(0.0);
        float weightSum = 0.0;

        float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);

        const int SAMPLES = 4;
        float offsetScale = radius / float(SAMPLES);

        for (int x = -SAMPLES; x <= SAMPLES; x++) {
            for (int y = -SAMPLES; y <= SAMPLES; y++) {
                float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;
                float distSq = dot(offset, offset);
                float radiusSq = radius * radius;

                if (distSq <= radiusSq) {
                    float weight = exp(-3.0 * distSq / radiusSq);
                    accum += content.eval(fragCoord + offset) * weight;
                    weightSum += weight;
                }
            }
        }

        return accum / weightSum;
    }
""".trimIndent()

/**
 * Progressive blur on both edges simultaneously.
 * Full shader path requires API 33+; below that, only the gradient overlays run.
 */
fun Modifier.progressiveBlur(
    blurRadius: Float,
    topHeight: Float = 0f,
    bottomHeight: Float = 0f,
    showGradientOverlay: Boolean = true,
    overlayAlpha: Float = 0.28f,
    overlayAlphaBottom: Float = overlayAlpha
): Modifier = composed {
    val overlayColorTop = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = overlayAlpha)
    val overlayColorBottom = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = overlayAlphaBottom)

    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && blurRadius > 0f) {
        Modifier.graphicsLayer {
            val shader = RuntimeShader(dualEdgeBlurAgsl)
            shader.setFloatUniform("blurRadius", blurRadius)
            shader.setFloatUniform("topHeight", topHeight)
            shader.setFloatUniform("bottomHeight", bottomHeight)
            shader.setFloatUniform("contentHeight", size.height)

            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }
    } else {
        Modifier
    }

    val gradientModifier = if (showGradientOverlay) {
        Modifier.drawWithContent {
            drawContent()
            if (topHeight > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(overlayColorTop, Color.Transparent),
                        endY = topHeight
                    )
                )
            }
            if (bottomHeight > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, overlayColorBottom),
                        startY = size.height - bottomHeight
                    )
                )
            }
        }
    } else {
        Modifier
    }

    this.then(blurModifier).then(gradientModifier)
}

/**
 * Single-edge progressive blur kept for backward compatibility.
 */
fun Modifier.progressiveBlur(
    blurRadius: Float,
    height: Float,
    direction: BlurDirection = BlurDirection.TOP,
    showGradientOverlay: Boolean = true
): Modifier = progressiveBlur(
    blurRadius = blurRadius,
    topHeight = if (direction == BlurDirection.TOP) height else 0f,
    bottomHeight = if (direction == BlurDirection.BOTTOM) height else 0f,
    showGradientOverlay = showGradientOverlay
)

/** Blur for [LazyColumn] under a transparent [LargeTopAppBar] (app bar is a sibling, not blurred). */
fun ProgressiveBlurStyle.applyToScrollableList(): Modifier = Modifier.progressiveBlur(
    blurRadius = blurRadius,
    topHeight = topHeightPx,
    bottomHeight = bottomHeightPx,
    showGradientOverlay = true,
    overlayAlpha = overlayAlpha,
    overlayAlphaBottom = overlayAlphaBottom
)

/** Blur for a full-screen layer (y=0 at window top), e.g. rule edit scroll under transparent chrome. */
fun ProgressiveBlurStyle.applyToFullBleedLayer(): Modifier = Modifier.progressiveBlur(
    blurRadius = blurRadius,
    topHeight = topHeightPx,
    bottomHeight = bottomHeightPx,
    showGradientOverlay = true,
    overlayAlpha = overlayAlpha,
    overlayAlphaBottom = overlayAlphaBottom
)
