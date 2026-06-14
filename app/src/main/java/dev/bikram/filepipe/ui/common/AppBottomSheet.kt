package dev.bikram.filepipe.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
    titleAccessory: (@Composable RowScope.() -> Unit)? = null,
    titleActions: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    actionsImePadding: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dismissSheet: () -> Unit = onDismiss
    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = { FilePipeBottomSheetDragHandle() },
    ) {
        FilePipePredictiveBackHandler(onBack = dismissSheet)
        Column(modifier = Modifier.navigationBarsPadding()) {
            if (showTitleBar) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        titleAccessory?.invoke(this)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                        titleActions?.invoke(this)
                    }
                }
            }
            val bodyModifier =
                Modifier
                    .fillMaxWidth()
                    .padding(contentPadding)
                    .let { modifier ->
                        if (scrollable) {
                            modifier.verticalScroll(rememberScrollState())
                        } else {
                            modifier
                        }
                    }
            Column(modifier = bodyModifier, content = content)
            if (actions != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .let { if (actionsImePadding) it.imePadding() else it }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}
