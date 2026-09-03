package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonElevation
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.ui.common.LocalAllowCompactControls
import dev.bikram.filepipe.ui.common.isSmallLandscape
import dev.bikram.filepipe.ui.feedback.LocalHapticEnabled
import dev.bikram.filepipe.ui.feedback.performLongPressHaptic

@Composable
fun rememberResponsiveActionButtonSize(
    defaultSize: Dp = 40.dp,
    compactSize: Dp = 34.dp,
    ultraCompactSize: Dp = 30.dp,
): Dp {
    val screenWidth =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        }
    val targetSize =
        when {
            screenWidth < 360.dp -> ultraCompactSize
            screenWidth < 430.dp -> compactSize
            else -> defaultSize
        }
    return targetSize
}

@Composable
fun rememberResponsiveActionIconSize(
    defaultSize: Dp = 20.dp,
    compactSize: Dp = 17.dp,
    ultraCompactSize: Dp = 15.dp,
): Dp {
    val screenWidth =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        }
    val targetSize =
        when {
            screenWidth < 360.dp -> ultraCompactSize
            screenWidth < 430.dp -> compactSize
            else -> defaultSize
        }
    return targetSize
}

@Composable
fun FilePipeActionLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun FilePipeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    tooltipLabel: String? = null,
    content: @Composable () -> Unit,
) {
    if (tooltipLabel != null) {
        val tooltipState = rememberTooltipState()
        val hapticEnabled = LocalHapticEnabled.current
        val view = LocalView.current
        val tooltipInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

        TooltipBox(
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                ),
            tooltip = {
                PlainTooltip {
                    Box(
                        modifier = Modifier.heightIn(min = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(tooltipLabel)
                    }
                }
            },
            state = tooltipState,
        ) {
            IconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = colors,
                interactionSource = tooltipInteractionSource,
                content = content,
            )
        }
        LaunchedEffect(tooltipState.isVisible) {
            if (tooltipState.isVisible && hapticEnabled) {
                view.performLongPressHaptic()
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
fun FilePipeFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    tooltipLabel: String? = null,
    content: @Composable () -> Unit,
) {
    if (tooltipLabel != null) {
        val tooltipState = rememberTooltipState()
        val hapticEnabled = LocalHapticEnabled.current
        val view = LocalView.current
        val tooltipInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

        TooltipBox(
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                ),
            tooltip = {
                PlainTooltip {
                    Box(
                        modifier = Modifier.heightIn(min = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(tooltipLabel)
                    }
                }
            },
            state = tooltipState,
        ) {
            FilledIconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = colors,
                interactionSource = tooltipInteractionSource,
                content = content,
            )
        }
        LaunchedEffect(tooltipState.isVisible) {
            if (tooltipState.isVisible && hapticEnabled) {
                view.performLongPressHaptic()
            }
        }
    } else {
        FilledIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
fun FilePipeFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    tooltipLabel: String? = null,
    content: @Composable () -> Unit,
) {
    if (tooltipLabel != null) {
        val tooltipState = rememberTooltipState()
        val hapticEnabled = LocalHapticEnabled.current
        val view = LocalView.current
        val tooltipInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

        TooltipBox(
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                ),
            tooltip = {
                PlainTooltip {
                    Box(
                        modifier = Modifier.heightIn(min = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(tooltipLabel)
                    }
                }
            },
            state = tooltipState,
        ) {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = colors,
                interactionSource = tooltipInteractionSource,
                content = content,
            )
        }
        LaunchedEffect(tooltipState.isVisible) {
            if (tooltipState.isVisible && hapticEnabled) {
                view.performLongPressHaptic()
            }
        }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
fun FilePipeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilePipeTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilePipeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilePipeFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilePipeToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ToggleButtonShapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
    // Compose M3 1.5.0-alpha27 renamed toggleButtonColors() to colors(), made ContentPadding
    // private in favor of contentPaddingFor(), and gave ToggleButton its own elevation type.
    colors: ToggleButtonColors = ToggleButtonDefaults.colors(),
    elevation: ToggleButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ToggleButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        shapes = shapes,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun rememberMaxLabelMinWidth(
    labels: List<String>,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.MaterialTheme.typography.labelLarge,
    extraPadding: androidx.compose.ui.unit.Dp = 28.dp,
): androidx.compose.ui.unit.Dp {
    val textMeasurer =
        androidx.compose.ui.text
            .rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    return androidx.compose.runtime.remember(labels, style, density) {
        val maxPx =
            labels.maxOfOrNull { label ->
                textMeasurer
                    .measure(
                        text = label,
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                    ).size.width
            } ?: 0
        with(density) { maxPx.toDp() } + extraPadding
    }
}

@Composable
fun FilePipeFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = androidx.compose.material3.contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    tooltipLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val sizeModifier =
        if (isSmallLandscape() && LocalAllowCompactControls.current) {
            Modifier.size(rememberResponsiveActionButtonSize())
        } else {
            Modifier
        }
    val guardedOnClick = {
        if (enabled) onClick()
    }
    if (tooltipLabel != null) {
        val tooltipState = rememberTooltipState()
        val hapticEnabled = LocalHapticEnabled.current
        val view = LocalView.current
        val tooltipInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

        TooltipBox(
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                ),
            tooltip = {
                PlainTooltip {
                    Box(
                        modifier = Modifier.heightIn(min = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(tooltipLabel)
                    }
                }
            },
            state = tooltipState,
        ) {
            FloatingActionButton(
                onClick = guardedOnClick,
                modifier = modifier.then(sizeModifier),
                shape = shape,
                containerColor = containerColor,
                contentColor = contentColor,
                elevation = elevation,
                interactionSource = tooltipInteractionSource,
                content = content,
            )
        }
        LaunchedEffect(tooltipState.isVisible) {
            if (tooltipState.isVisible && hapticEnabled) {
                view.performLongPressHaptic()
            }
        }
    } else {
        FloatingActionButton(
            onClick = guardedOnClick,
            modifier = modifier.then(sizeModifier),
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = elevation,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
fun FilePipeExtendedFloatingActionButton(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = androidx.compose.material3.contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
) {
    ExtendedFloatingActionButton(
        text = text,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        expanded = expanded,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        interactionSource = interactionSource,
    )
}

@Composable
fun FilePipeSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
    contentColor: Color = androidx.compose.material3.contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilePipeElevatedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.elevatedShape,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    elevation: CardElevation = CardDefaults.elevatedCardElevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilePipeOutlinedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.outlinedShape,
    colors: CardColors = CardDefaults.outlinedCardColors(),
    elevation: CardElevation = CardDefaults.outlinedCardElevation(),
    border: BorderStroke = CardDefaults.outlinedCardBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilePipeFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = androidx.compose.material3.FilterChipDefaults.shape,
    colors: androidx.compose.material3.SelectableChipColors =
        androidx.compose.material3.FilterChipDefaults
            .filterChipColors(),
    elevation: androidx.compose.material3.SelectableChipElevation? =
        androidx.compose.material3.FilterChipDefaults
            .filterChipElevation(),
    border: BorderStroke? =
        androidx.compose.material3.FilterChipDefaults
            .filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}

@Composable
fun FilePipeInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    avatar: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = androidx.compose.material3.InputChipDefaults.shape,
    colors: androidx.compose.material3.SelectableChipColors =
        androidx.compose.material3.InputChipDefaults
            .inputChipColors(),
    elevation: androidx.compose.material3.SelectableChipElevation? =
        androidx.compose.material3.InputChipDefaults
            .inputChipElevation(),
    border: BorderStroke? =
        androidx.compose.material3.InputChipDefaults
            .inputChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    InputChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        avatar = avatar,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}

@Composable
fun FilePipeDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors =
        androidx.compose.material3.MenuDefaults
            .itemColors(),
    contentPadding: PaddingValues = androidx.compose.material3.MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    )
}

@Composable
fun FilePipeTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    selectedContentColor: Color = androidx.compose.material3.LocalContentColor.current,
    unselectedContentColor: Color = selectedContentColor,
    interactionSource: MutableInteractionSource? = null,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        text = text,
        icon = icon,
        selectedContentColor = selectedContentColor,
        unselectedContentColor = unselectedContentColor,
        interactionSource = interactionSource,
    )
}
