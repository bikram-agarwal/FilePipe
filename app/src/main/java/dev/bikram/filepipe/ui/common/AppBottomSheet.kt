package dev.bikram.filepipe.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.ui.components.FilePipeBottomSheetDragHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    showTitleBar: Boolean = true,
    sheetState: SheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    sheetGesturesEnabled: Boolean = true,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    subtitleSpacing: Dp = 6.dp,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    subtitleContent: (@Composable () -> Unit)? = null,
    titleLeading: (@Composable RowScope.() -> Unit)? = null,
    titleAccessory: (@Composable RowScope.() -> Unit)? = null,
    titleActions: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    actionsImePadding: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dismissSheet: () -> Unit = onDismiss
    // ModalBottomSheet renders in its own window, which resets LocalDensity to the RAW device
    // density/fontScale — the app-wide font/display cap from FilePipeTheme does NOT propagate
    // into it. Capture the capped density here (outside the sheet window) and re-provide it
    // inside, so sheet text matches the rest of the app instead of rendering at the uncapped OS
    // font size. Do NOT remove this wrapper. (Kept in parity with Remember's AppBottomSheet.)
    val cappedDensity = LocalDensity.current
    // Landscape windows are short; compact the sheet chrome (drag handle, title, action bar) so
    // it doesn't consume most of the height and leave only a sliver for content.
    val landscape = isLandscape()
    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = { FilePipeBottomSheetDragHandle(compact = landscape) },
    ) {
        CompositionLocalProvider(LocalDensity provides cappedDensity) {
            FilePipePredictiveBackHandler(onBack = dismissSheet)
            // When the keyboard is up in landscape there is almost no room above it; hide the
            // title bar entirely so the field being edited gets that space (the title is least
            // useful precisely when the user is typing into a field).
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val showTitle = showTitleBar && !(landscape && imeVisible)
            Column(modifier = Modifier.navigationBarsPadding()) {
                if (showTitle) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = if (landscape) 0.dp else 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            titleLeading?.invoke(this)
                            titleAccessory?.invoke(this)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    // Smaller title in landscape so the header doesn't eat a third
                                    // of a short sheet.
                                    style =
                                        if (landscape) {
                                            MaterialTheme.typography.titleMedium
                                        } else {
                                            MaterialTheme.typography.titleLargeEmphasized
                                        },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // Subtitle is secondary help text; drop it in landscape to reclaim a line.
                                if (!landscape) {
                                    if (subtitleContent != null) {
                                        Column(modifier = Modifier.padding(top = subtitleSpacing)) {
                                            subtitleContent()
                                        }
                                    } else if (!subtitle.isNullOrBlank()) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = subtitleSpacing),
                                        )
                                    }
                                }
                            }
                            titleActions?.invoke(this)
                        }
                    }
                }
                val bodyModifier =
                    Modifier
                        .fillMaxWidth()
                        .let { if (scrollable) it.weight(1f, fill = false) else it }
                        .padding(contentPadding)
                        .let { modifier ->
                            if (scrollable) {
                                modifier.verticalScroll(rememberScrollState())
                            } else {
                                modifier
                            }
                        }
                Column(modifier = bodyModifier, content = content)
                // While typing in landscape the action bar (Cancel/Reset/Save) is as useless as
                // the title — hide it too so the field gets the room. It returns when the keyboard
                // closes; the IME action key / a keyboard dismiss brings Save back within reach.
                if (actions != null && !(landscape && imeVisible)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .let { if (actionsImePadding) it.imePadding() else it }
                                .padding(horizontal = 20.dp, vertical = if (landscape) 4.dp else 12.dp),
                    ) {
                        // Shrink the enforced touch-target height of the action buttons in landscape
                        // so the button bar stays compact on short windows.
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides if (landscape) 36.dp else 48.dp,
                        ) {
                            SheetActionButtons(spacing = 8.dp, content = actions)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lays the sheet's action buttons out in a single end-aligned row, falling back to a stacked
 * column ONLY when the buttons genuinely don't fit the available width. The buttons' real widths
 * are measured (via [SubcomposeLayout]) rather than guessed from screen width + font scale, so
 * short labels like Cancel/Reset/Save stay on one row even at large display sizes — where a
 * width-based heuristic would wrongly stack them. (Kept in parity with Remember's AppBottomSheet.)
 */
@Composable
private fun SheetActionButtons(
    spacing: Dp,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        val spacingPx = spacing.roundToPx()
        // Measure the buttons unconstrained to learn their natural widths.
        val measured = subcompose("measure", content).map { it.measure(Constraints()) }
        val naturalRowWidth =
            measured.sumOf { it.width } + spacingPx * (measured.size - 1).coerceAtLeast(0)
        val fitsInRow = measured.isNotEmpty() && naturalRowWidth <= constraints.maxWidth

        val placeable =
            subcompose("content") {
                if (fitsInRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        content()
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        content()
                    }
                }
            }.first().measure(constraints)

        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberBottomSheetStateWithUnsavedChanges(
    isDirty: Boolean,
    onShowDialog: () -> Unit,
): SheetState {
    val currentIsDirty = rememberUpdatedState(isDirty)
    val currentOnShowDialog = rememberUpdatedState(onShowDialog)
    return rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange =
            remember {
                { sheetValue ->
                    if (sheetValue == SheetValue.Hidden && currentIsDirty.value) {
                        currentOnShowDialog.value()
                        false
                    } else {
                        true
                    }
                }
            },
    )
}
