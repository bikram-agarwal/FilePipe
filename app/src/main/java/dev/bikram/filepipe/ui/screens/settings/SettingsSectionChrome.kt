package dev.bikram.filepipe.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.components.FilePipeExpandableSectionHeader
import dev.bikram.filepipe.ui.components.FilePipeSwitch
import dev.bikram.filepipe.ui.feedback.appClickable
import dev.bikram.filepipe.ui.navigation.DEV_OPTIONS_SHARED_BOUNDS_KEY
import dev.bikram.filepipe.ui.navigation.LocalNavAnimatedVisibilityScope
import dev.bikram.filepipe.ui.navigation.LocalSharedTransitionScope
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun SettingsExpandableSection(
    sectionKey: String,
    iconName: String,
    title: String,
    collapsedSectionKeys: Set<String>,
    onCollapsedSectionKeysChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    forceExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    val collapsed = !forceExpanded && sectionKey in collapsedSectionKeys
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec<IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    Column(modifier = modifier) {
        if (showHeader) {
            FilePipeExpandableSectionHeader(
                iconName = iconName,
                title = title,
                collapsed = collapsed,
                onToggle = {
                    onCollapsedSectionKeysChange(
                        if (collapsed) {
                            collapsedSectionKeys - sectionKey
                        } else {
                            collapsedSectionKeys + sectionKey
                        },
                    )
                },
            )
            AnimatedVisibility(
                visible = !collapsed,
                enter =
                    expandVertically(
                        animationSpec = spatialSpec,
                        expandFrom = Alignment.Top,
                    ) + fadeIn(fadeInSpec),
                exit =
                    shrinkVertically(
                        animationSpec = spatialSpec,
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(fadeOutSpec),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            }
        } else {
            Column {
                content()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun SettingsStandaloneNavigationRow(
    iconName: String,
    title: String,
    onClick: () -> Unit,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val sharedBoundsModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(DEV_OPTIONS_SHARED_BOUNDS_KEY),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        } else {
            Modifier
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(sharedBoundsModifier)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .appClickable(
                    onClick = onClick,
                    indication = null,
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)),
            contentAlignment = Alignment.Center,
        ) {
            FilePipeMaterialRoundedSymbol(
                name = iconName,
                contentDescription = null,
                size = 21.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SettingsArrowOutwardBadge()
    }
}

/** Trailing badge for settings rows that leave the settings list (Developer options, ObtainX). */
@Composable
internal fun SettingsArrowOutwardBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(32.dp)
                .clip(MaterialTheme.shapes.extraExtraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        FilePipeMaterialRoundedSymbol(
            name = "arrow_outward",
            contentDescription = null,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SettingsSectionHeader(
    iconName: String,
    title: String,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = if (trailingContent != null) Modifier.fillMaxWidth() else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilePipeMaterialRoundedSymbol(
            name = iconName,
            contentDescription = null,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (trailingContent != null) {
            Spacer(Modifier.weight(1f))
            trailingContent()
        }
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconName: String? = null,
    switchEnabled: Boolean = true,
    onDisabledInteraction: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .appClickable {
                    if (!switchEnabled) {
                        onDisabledInteraction?.invoke()
                    } else {
                        onCheckedChange(!checked)
                    }
                }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconName != null) {
            FilePipeMaterialRoundedSymbol(
                name = iconName,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        val switchInteractive = switchEnabled || onDisabledInteraction != null
        FilePipeSwitch(
            checked = checked,
            onCheckedChange = { enabled ->
                when {
                    switchEnabled -> {
                        onCheckedChange(enabled)
                    }

                    onDisabledInteraction != null && enabled -> {
                        onDisabledInteraction.invoke()
                    }

                    else -> { }
                }
            },
            enabled = switchInteractive,
        )
    }
}
