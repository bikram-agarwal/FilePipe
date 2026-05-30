package dev.bikram.filepipe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import dev.bikram.filepipe.ui.feedback.performSwipeThresholdHaptic
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Horizontal swipe with an explicit commit threshold (fraction of card width).
 * Used instead of [androidx.compose.material3.SwipeToDismissBox] because custom
 * [positionalThreshold] is unreliable in Material3 1.4+ (often stuck at ~50%).
 */
@Composable
fun DeliberateSwipeRevealCard(
    commitThresholdFraction: Float,
    cardShape: Shape,
    onSwipeStartToEnd: () -> Unit,
    onSwipeEndToStart: () -> Unit,
    hapticEnabled: Boolean,
    backgroundContent: @Composable BoxScope.(draggingFromStart: Boolean, revealProgress: Float) -> Unit,
    modifier: Modifier = Modifier,
    allowSwipeStartToEnd: Boolean = true,
    allowSwipeEndToStart: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var laidOutWidthPx by remember { mutableFloatStateOf(0f) }
    val settleAnimationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())

    BoxWithConstraints(modifier = modifier.clip(cardShape)) {
        val constraintWidthPx =
            if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat().coerceAtLeast(1f) else 0f
        val widthPx =
            when {
                laidOutWidthPx > 0f -> laidOutWidthPx
                constraintWidthPx > 0f -> constraintWidthPx
                else -> 0f
            }
        val dragClampPx = if (widthPx > 0f) widthPx else 10_000f
        val thresholdPx =
            if (widthPx > 0f) widthPx * commitThresholdFraction else Float.POSITIVE_INFINITY

        LaunchedEffect(hapticEnabled, widthPx, commitThresholdFraction) {
            var previousBeyond = false
            snapshotFlow {
                widthPx > 0f && abs(offsetX) >= thresholdPx
            }.collect { beyond ->
                if (beyond && !previousBeyond && hapticEnabled) {
                    view.performSwipeThresholdHaptic()
                }
                previousBeyond = beyond
            }
        }

        SubcomposeLayout(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    laidOutWidthPx = size.width.toFloat()
                },
        ) { layoutConstraints ->
            val foregroundMeasurable =
                subcompose("foreground") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                            .pointerInput(
                                dragClampPx,
                                thresholdPx,
                                allowSwipeStartToEnd,
                                allowSwipeEndToStart,
                            ) {
                                val minOffset = if (allowSwipeEndToStart) -dragClampPx else 0f
                                val maxOffset = if (allowSwipeStartToEnd) dragClampPx else 0f
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dragAmount ->
                                        offsetX = (offsetX + dragAmount).coerceIn(minOffset, maxOffset)
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            when {
                                                allowSwipeStartToEnd && offsetX >= thresholdPx -> {
                                                    onSwipeStartToEnd()
                                                    offsetX = 0f
                                                }

                                                allowSwipeEndToStart && offsetX <= -thresholdPx -> {
                                                    onSwipeEndToStart()
                                                    offsetX = 0f
                                                }

                                                else -> {
                                                    val start = offsetX
                                                    val anim = Animatable(start)
                                                    anim.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = settleAnimationSpec,
                                                    ) {
                                                        offsetX = value
                                                    }
                                                }
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        content()
                    }
                }.first()
            val foregroundPlaceable = foregroundMeasurable.measure(layoutConstraints)
            val cardWidth = foregroundPlaceable.width
            val cardHeight = foregroundPlaceable.height
            val fixed = Constraints.fixed(cardWidth, cardHeight)
            val backgroundMeasurable =
                subcompose("background") {
                    val revealProgress =
                        if (thresholdPx.isFinite() && thresholdPx > 0f) {
                            (abs(offsetX) / thresholdPx).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    Box(Modifier.fillMaxSize()) {
                        when {
                            offsetX > 4f -> backgroundContent(true, revealProgress)
                            offsetX < -4f -> backgroundContent(false, revealProgress)
                        }
                    }
                }.first()
            val backgroundPlaceable = backgroundMeasurable.measure(fixed)
            layout(cardWidth, cardHeight) {
                backgroundPlaceable.place(0, 0)
                foregroundPlaceable.place(0, 0)
            }
        }
    }
}
