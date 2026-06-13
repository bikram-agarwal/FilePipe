package dev.bikram.filepipe.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.graphics.shapes.Morph
import dev.bikram.filepipe.R
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.common.isSmallLandscape
import dev.bikram.filepipe.ui.theme.MorphPolygonShape
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import kotlin.math.roundToInt

// Wholesale port of Remember's alert chrome (AlertChrome.kt), minus the
// reminder-notifications bar that has no FilePipe equivalent. Keep the two files in
// sync when changing either app's alert FAB or bars.

@Immutable
data class AlertChromeSummary(
    val count: Int,
    val iconName: String,
    val isNotificationPriority: Boolean,
)

fun alertChromeSummary(updateState: UpdateChromeState): AlertChromeSummary {
    val hasUpdateAlert = updateState != UpdateChromeState.Hidden
    return AlertChromeSummary(
        count = if (hasUpdateAlert) 1 else 0,
        iconName =
            when {
                updateState == UpdateChromeState.ReadyToInstall -> "download_done"
                hasUpdateAlert -> "download"
                else -> "download"
            },
        isNotificationPriority = false,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlertFloatingFab(
    summary: AlertChromeSummary,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (summary.count <= 0) return

    val isSmallLandscape = isSmallLandscape()

    val label = stringResource(R.string.main_alert_fab_label)
    val scheme = MaterialTheme.colorScheme
    val closedContainerColor =
        when {
            summary.isNotificationPriority -> scheme.error
            else -> scheme.primaryContainer
        }
    val closedContentColor =
        when {
            summary.isNotificationPriority -> scheme.onError
            else -> scheme.onPrimaryContainer
        }
    val openContainerColor = scheme.surfaceVariant
    val openContentColor = scheme.onSurfaceVariant
    val contentColor by animateColorAsState(
        targetValue = if (expanded) openContentColor else closedContentColor,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec()),
        label = "alert_fab_icon_color",
    )
    val fabMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val shapeProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec()),
        label = "alert_fab_shape_morph",
    )
    val fabShape = MorphPolygonShape(fabMorph, shapeProgress)
    val fabSize = if (isSmallLandscape) rememberResponsiveActionButtonSize() else 56.dp
    val scaleFactor = if (isSmallLandscape) fabSize.value / 56f else 1f
    val density = LocalDensity.current
    val iconTravelPx = with(density) { (14.dp * scaleFactor).toPx() }
    val alertIconAlpha = 1f - shapeProgress
    val chevronAlpha = shapeProgress

    Box(
        modifier = modifier.size(fabSize),
        contentAlignment = Alignment.Center,
    ) {
        ToggleFloatingActionButton(
            checked = expanded,
            onCheckedChange = { onClick() },
            containerColor =
                ToggleFloatingActionButtonDefaults.containerColor(
                    initialColor = closedContainerColor,
                    finalColor = openContainerColor,
                ),
            modifier =
                Modifier
                    .size(fabSize)
                    .shadow(
                        elevation = 2.dp,
                        shape = fabShape,
                        clip = false,
                    ).clip(fabShape)
                    .semantics { contentDescription = label },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FilePipeMaterialRoundedSymbol(
                    name = summary.iconName,
                    size = 28.dp * scaleFactor,
                    tint = contentColor,
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = alertIconAlpha
                            translationY = -iconTravelPx * shapeProgress
                        },
                )
                FilePipeMaterialRoundedSymbol(
                    name = "chevron_right",
                    size = 28.dp * scaleFactor,
                    tint = contentColor,
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .offset(y = 1.dp * scaleFactor)
                            .graphicsLayer {
                                alpha = chevronAlpha
                                rotationZ = 90f
                                translationY = iconTravelPx * (1f - shapeProgress)
                            },
                )
            }
        }
        AlertFabBadge(
            count = summary.count,
            expanded = expanded,
            notificationPriority = summary.isNotificationPriority,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-4).dp * scaleFactor, y = 4.dp * scaleFactor),
        )
    }
}

@Composable
private fun AlertFabBadge(
    count: Int,
    expanded: Boolean,
    notificationPriority: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor =
        when {
            expanded -> scheme.inverseSurface
            notificationPriority -> scheme.errorContainer
            else -> scheme.primary
        }
    val contentColor =
        when {
            expanded -> scheme.inverseOnSurface
            notificationPriority -> scheme.onErrorContainer
            else -> scheme.onPrimary
        }
    Surface(
        modifier = modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlertFloatingActionButtonMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    summary: AlertChromeSummary,
    updateState: UpdateChromeState,
    onUpdateClick: () -> Unit,
    onDismissUpdateAvailable: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    // Phone pill mode centers the unfurled bars over the whole chrome strip; rail mode
    // keeps them anchored to the FAB.
    centerBarsInWindow: Boolean = false,
    // Rail mode caps the bars to the list pane so they never cross into the detail pane.
    barsMaxWidth: Dp = Dp.Unspecified,
) {
    if (summary.count <= 0) return

    // Deliberately NOT built on M3's FloatingActionButtonMenu: its item column clips to
    // its own bounds, whose origin sits at the FAB's left edge — window-centered bars
    // must extend left of the FAB, which is impossible inside that clip. The FAB anchors
    // a plain Box instead, and the bars render as an unclipped sibling placed above it.
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    val isSmallLandscape = isSmallLandscape()
    val barIconSize = if (isSmallLandscape) rememberResponsiveActionButtonSize() else 44.dp
    val barContentScale = if (isSmallLandscape) barIconSize.value / 44f else 1f
    val density = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    var anchorLeftInWindow by remember { mutableIntStateOf(0) }
    var anchorPlaced by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier.onPlaced { coordinates ->
                anchorLeftInWindow = coordinates.boundsInWindow().left.roundToInt()
                anchorPlaced = true
            },
    ) {
        AlertFloatingFab(
            summary = summary,
            expanded = expanded,
            onClick = { onExpandedChange(!expanded) },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            modifier =
                Modifier.layout { measurable, _ ->
                    val barsConstraints =
                        if (centerBarsInWindow) {
                            Constraints(maxWidth = windowWidthPx)
                        } else {
                            Constraints()
                        }
                    val placeable = measurable.measure(barsConstraints)
                    // Zero-sized so the anchor keeps the FAB's footprint; the bars draw
                    // above (and, when window-centered, left of) the anchor freely.
                    layout(0, 0) {
                        val x =
                            if (centerBarsInWindow) {
                                ((windowWidthPx - placeable.width) / 2f).roundToInt() - anchorLeftInWindow
                            } else {
                                0
                            }
                        placeable.place(x, -(placeable.height + 20.dp.roundToPx()))
                    }
                },
        ) {
            val progress by
                transition.animateFloat(
                    transitionSpec = {
                        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
                    },
                    label = "alert_bars_unfurl",
                ) { state ->
                    if (state == EnterExitState.Visible) 1f else 0f
                }
            val exiting =
                transition.currentState == EnterExitState.Visible &&
                    transition.targetState == EnterExitState.PostExit
            val barAlpha = if (exiting) progress else 1f
            val barsMinWidth =
                if (barsMaxWidth.isSpecified) {
                    minOf(332.dp * barContentScale, barsMaxWidth)
                } else {
                    332.dp * barContentScale
                }
            val barsCapWidth =
                if (barsMaxWidth.isSpecified) {
                    minOf(392.dp * barContentScale, barsMaxWidth)
                } else {
                    392.dp * barContentScale
                }
            Column(
                modifier =
                    Modifier
                        .widthIn(min = barsMinWidth, max = barsCapWidth)
                        .padding(
                            start = 6.dp * barContentScale,
                            end = 6.dp * barContentScale,
                        ).graphicsLayer {
                            translationY = with(density) { 18.dp.toPx() } * (1f - progress)
                            alpha = if (centerBarsInWindow && !anchorPlaced) 0f else 1f
                        },
                verticalArrangement = Arrangement.spacedBy(10.dp * barContentScale),
            ) {
                if (updateState != UpdateChromeState.Hidden) {
                    UpdateFloatingBar(
                        state = updateState,
                        onCheckClick = onUpdateClick,
                        onDismissAvailable = onDismissUpdateAvailable,
                        onInstallClick = onInstallUpdate,
                        contentAlpha = barAlpha,
                        shadowAlpha = barAlpha,
                        iconContainerSize = barIconSize,
                        contentScale = barContentScale,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
