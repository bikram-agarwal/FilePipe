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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalLayoutDirection
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
import dev.bikram.filepipe.ui.common.LocalAllowCompactControls
import dev.bikram.filepipe.ui.common.isSmallLandscape
import dev.bikram.filepipe.ui.theme.MorphPolygonShape
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import kotlin.math.roundToInt

// Wholesale port of Remember's alert chrome (AlertChrome.kt), minus the
// reminder-notifications bar that has no FilePipe equivalent. Keep the two files in
// sync when changing either app's alert FAB or bars.

/** Fallback width for bars anchored to the FAB, where the room to their right isn't known. */
private val AlertBarsAnchoredWidth = 392.dp

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

    // The bars' own content stays compact on short landscape windows either way; this only governs
    // the FAB, which has to match the other FAB in whatever chrome is hosting it.
    val useCompactFab = isSmallLandscape() && LocalAllowCompactControls.current

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
    val fabSize = if (useCompactFab) rememberResponsiveActionButtonSize() else 56.dp
    val scaleFactor = if (useCompactFab) fabSize.value / 56f else 1f
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
    val layoutDirection = LocalLayoutDirection.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    // Landscape 3-button nav takes a side edge, so the bars must centre on the area actually left
    // for content. Centring on the raw window width pushes them visibly toward the nav bar.
    val navigationBarInsets = WindowInsets.navigationBars
    val barsAreaLeftPx = navigationBarInsets.getLeft(density, layoutDirection)
    val barsAreaWidthPx =
        (windowWidthPx - barsAreaLeftPx - navigationBarInsets.getRight(density, layoutDirection))
            .coerceAtLeast(0)
    var anchorLeftInWindow by remember { mutableFloatStateOf(0f) }
    var anchorScale by remember { mutableFloatStateOf(1f) }
    var anchorPlaced by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier.onPlaced { coordinates ->
                val anchorBounds = coordinates.boundsInWindow()
                anchorLeftInWindow = anchorBounds.left
                anchorScale =
                    if (coordinates.size.width > 0) {
                        (anchorBounds.width / coordinates.size.width).coerceAtLeast(0.01f)
                    } else {
                        1f
                    }
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
                            // The content area is a window measurement, but the bars are measured in
                            // the anchor's own (possibly scaled) space, so the ceiling has to be too.
                            Constraints(maxWidth = (barsAreaWidthPx / anchorScale).roundToInt())
                        } else {
                            Constraints()
                        }
                    val placeable = measurable.measure(barsConstraints)
                    // Zero-sized so the anchor keeps the FAB's footprint; the bars draw above and,
                    // being wider than it, to the right of the anchor freely.
                    layout(0, 0) {
                        // x = 0 lands the bars' left edge on the anchor's left edge, and the anchor
                        // is the leading FAB. barsWidth below does the other half of the job by
                        // ending the bars on the trailing FAB's right edge.
                        placeable.place(0, -(placeable.height + 20.dp.roundToPx()))
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
            // Pill mode spans the chrome strip exactly: the bars' left edge sits on the leading
            // FAB's left edge and their right edge on the trailing FAB's right edge. The pill is
            // centred in the content area with the two FABs symmetric either side of it, so the gap
            // between the content area's left edge and the leading FAB - which is this composable's
            // anchor - is also the gap on the right, and that's all the geometry needed here.
            val barsStripSpanWidth =
                with(density) {
                    ((barsAreaWidthPx - (anchorLeftInWindow - barsAreaLeftPx) * 2f) / anchorScale)
                        .coerceAtLeast(0f)
                        .toDp()
                }
            val barsWidth =
                when {
                    // Rail mode: stay inside the pane the bars belong to instead of spanning a strip
                    // that doesn't exist there.
                    barsMaxWidth.isSpecified -> minOf(AlertBarsAnchoredWidth * barContentScale, barsMaxWidth)

                    centerBarsInWindow -> barsStripSpanWidth

                    else -> AlertBarsAnchoredWidth * barContentScale
                }
            Column(
                modifier =
                    Modifier
                        // Exactly the strip span, with no horizontal padding: any inset here would
                        // pull the bars' edges off the FAB edges they're supposed to line up with.
                        .width(barsWidth)
                        .graphicsLayer {
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
