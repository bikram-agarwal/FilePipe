package dev.bikram.filepipe.ui.screens.rules

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.ui.components.RuleCard
import dev.bikram.filepipe.ui.components.ThemeColoredEmptyRulesIllustration
import dev.bikram.filepipe.ui.components.RuleCardAction
import dev.bikram.filepipe.ui.feedback.LocalHapticEnabled
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound
import dev.bikram.filepipe.ui.components.DeliberateSwipeRevealCard
import dev.bikram.filepipe.ui.components.SwipeDismissCardDefaults
import dev.bikram.filepipe.ui.modifiers.applyToScrollableList
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.gradientOverlayTopAppBarColors
import dev.bikram.filepipe.ui.navigation.LocalPrimaryTabTopBanner
import dev.bikram.filepipe.ui.navigation.LocalPrimaryTabTopBannerActive
import dev.bikram.filepipe.ui.navigation.Screen
import dev.bikram.filepipe.ui.theme.semanticSwipeBackground
import dev.bikram.filepipe.ui.theme.semanticSwipeIconTint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    contentPadding: PaddingValues,
    onEditRule: (Long) -> Unit,
    onNavigateToHistoryDetail: (Long) -> Unit,
    onNavigateToHistoryList: () -> Unit,
    onNavigateToRuleHistory: (Long) -> Unit,
    viewModel: RulesViewModel = hiltViewModel()
) {
    val playTap = rememberPlayTapSound()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val rules = uiState.rules
    val sortKey = uiState.sortKey
    val sortDirection = uiState.sortDirection
    val selectedRuleIds = uiState.selectedRuleIds
    val progressMap = uiState.progressMap
    val isRunning = uiState.isRunning
    val manualRunCancelAnchor = uiState.manualRunCancelAnchor
    val isCompactMode = uiState.isCompactMode
    val cardModeOverrides = uiState.cardModeOverrides
    val swipeStartToEnd = uiState.swipeStartToEnd
    val swipeEndToStart = uiState.swipeEndToStart
    val staleRuleIds = uiState.staleRuleIds
    val previewState = uiState.previewState
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    var pendingDeleteRule by remember { mutableStateOf<Rule?>(null) }
    var pendingDeleteSelected by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBlurModifier = LocalProgressiveBlurStyle.current?.applyToScrollableList() ?: Modifier

    val hasSelection = selectedRuleIds.isNotEmpty()
    val lazyListState = rememberLazyListState()
    var reorderableRules by remember { mutableStateOf(rules) }
    var dragActuallyMoved by remember { mutableStateOf(false) }
    var previousSortKey by remember { mutableStateOf<HistorySortKey?>(null) }
    var previousSortDirection by remember { mutableStateOf<HistorySortDirection?>(null) }
    LaunchedEffect(sortKey, sortDirection, rules) {
        val sortModeChanged =
            previousSortKey != sortKey || previousSortDirection != sortDirection
        previousSortKey = sortKey
        previousSortDirection = sortDirection
        if (rules.isEmpty()) {
            reorderableRules = emptyList()
            return@LaunchedEffect
        }
        if (sortModeChanged) {
            reorderableRules = rules
            lazyListState.scrollToItem(0)
            return@LaunchedEffect
        }
        if (reorderableRules.isEmpty()) {
            reorderableRules = rules
            return@LaunchedEffect
        }
        val reorderRuleIds = reorderableRules.map { it.id }.toSet()
        val hasRuleNotInReorderList = rules.any { rule -> rule.id !in reorderRuleIds }
        if (hasRuleNotInReorderList) {
            reorderableRules = rules
            return@LaunchedEffect
        }
        val freshByRuleId = rules.associateBy { it.id }
        reorderableRules = reorderableRules.mapNotNull { rule -> freshByRuleId[rule.id] }
    }
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        dragActuallyMoved = true
        reorderableRules = reorderableRules.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }
    val sortKeyState = rememberUpdatedState(sortKey)

    BackHandler(enabled = hasSelection) {
        viewModel.clearSelection()
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateAfterRun.collect { target ->
            when (target) {
                is RulesRunNavigation.HistoryDetail -> onNavigateToHistoryDetail(target.historyId)
                is RulesRunNavigation.HistoryList -> onNavigateToHistoryList()
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.deleteUndoEvent.collect { event ->
            val count = event.rules.size
            val label = if (count == 1) "\"${event.rules.first().name}\" deleted" else "$count rules deleted"
            val result = snackbarHostState.showSnackbar(
                message = label,
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(event.rules)
            }
        }
    }

    LaunchedEffect(userMessage) {
        val msg = userMessage ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        } finally {
            viewModel.clearUserMessage()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel, snackbarHostState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.refreshStaleFolderAccess()
                Lifecycle.Event.ON_STOP -> snackbarHostState.currentSnackbarData?.dismiss()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = if (LocalUseGradientBackground.current) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                LocalPrimaryTabTopBanner.current()
                LargeTopAppBar(
                    modifier = Modifier.then(
                        if (LocalPrimaryTabTopBannerActive.current) {
                            Modifier.consumeWindowInsets(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                        } else {
                            Modifier
                        }
                    ),
                    title = { Text("Rules") },
                    scrollBehavior = scrollBehavior,
                    colors = gradientOverlayTopAppBarColors(),
                    actions = {
                        if (hasSelection && !isRunning) {
                            IconButton(onClick = {
                                playTap()
                                viewModel.selectAll()
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.run_select_all))
                            }
                            IconButton(onClick = {
                                playTap()
                                viewModel.clearSelection()
                            }) {
                                Icon(Icons.Default.Deselect, contentDescription = stringResource(R.string.run_deselect_all))
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box {
                                    FilledTonalIconButton(onClick = {
                                        playTap()
                                        sortMenuExpanded = true
                                    }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = stringResource(R.string.history_sort_menu)
                                        )
                                    }
                                    RulesSortDropdown(
                                        expanded = sortMenuExpanded,
                                        onDismiss = { sortMenuExpanded = false },
                                        sortKey = sortKey,
                                        sortDirection = sortDirection,
                                        onSelect = { key, direction ->
                                            playTap()
                                            viewModel.setSort(key, direction)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                                FilledTonalIconButton(onClick = {
                                    playTap()
                                    viewModel.toggleGlobalViewMode()
                                }) {
                                    Icon(
                                        imageVector = if (isCompactMode) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                        contentDescription = if (isCompactMode) "Expand all" else "Collapse all"
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding())
            )
        },
        bottomBar = {
            when {
                isRunning && manualRunCancelAnchor == ManualRunCancelAnchor.RunSelectedBar -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = contentPadding.calculateBottomPadding() + 8.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 3.dp,
                            shadowElevation = 3.dp
                        ) {
                            OutlinedButton(
                                onClick = { playTap(); viewModel.cancelManualRun() },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
                isRunning -> { }
                else -> {
                    AnimatedVisibility(
                        visible = hasSelection,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        val enabledSelectedCount = rules.count { it.id in selectedRuleIds && it.isEnabled }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 3.dp,
                                shadowElevation = 3.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilledTonalIconButton(onClick = { playTap(); viewModel.clearSelection() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                                    }
                                    FilledTonalIconButton(
                                        onClick = { playTap(); pendingDeleteSelected = true },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                                    }
                                    FilledTonalButton(
                                        onClick = { playTap(); viewModel.runSelected() },
                                        enabled = enabledSelectedCount > 0,
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text("Run Selected (${selectedRuleIds.size})")
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (rules.isEmpty()) {
            EmptyState(
                onAddRule = {
                    playTap()
                    onEditRule(Screen.RuleDetail.NEW_RULE_ID)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(scrollBlurModifier)
                    .padding(innerPadding)
                    .padding(bottom = contentPadding.calculateBottomPadding())
            )
        } else {
            val reorderLongPressActive = !isRunning
            val listColumnPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding() + 56.dp
            )
            val listModifier = Modifier
                .fillMaxSize()
                .then(scrollBlurModifier)

            LazyColumn(
                state = lazyListState,
                modifier = listModifier,
                contentPadding = listColumnPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reorderableRules, key = { it.id }) { rule ->
                    ReorderableItem(
                        reorderableLazyListState,
                        rule.id,
                        modifier = Modifier.animateItem()
                    ) { isDragging ->
                        val dragElevation by animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 0.dp,
                            label = "ruleCardReorderShadow"
                        )
                        val reorderLongPressModifier = if (reorderLongPressActive) {
                            Modifier.longPressDraggableHandle(
                                onDragStarted = { _ -> dragActuallyMoved = false },
                                onDragStopped = {
                                    if (!dragActuallyMoved) {
                                        viewModel.toggleSelection(rule.id)
                                    } else {
                                        viewModel.applyDraggedOrder(
                                            reorderableRules,
                                            alsoSwitchSortToMyOrder = sortKeyState.value != HistorySortKey.MY_ORDER
                                        )
                                    }
                                    dragActuallyMoved = false
                                }
                            )
                        } else {
                            Modifier
                        }
                        val isExpanded =
                            viewModel.isCardExpanded(rule.id, isCompactMode, cardModeOverrides)
                        val showInlineProgressCancel =
                            manualRunCancelAnchor is ManualRunCancelAnchor.SingleRule &&
                                manualRunCancelAnchor.ruleId == rule.id
                        SwipeToDismissRuleCard(
                            rule = rule,
                            isSelected = rule.id in selectedRuleIds,
                            isExpanded = isExpanded,
                            progress = progressMap[rule.id],
                            isAnyRuleRunning = isRunning,
                            hasStaleFolder = rule.id in staleRuleIds,
                            onStaleWarningClick = { onEditRule(rule.id) },
                            swipeStartToEnd = swipeStartToEnd,
                            swipeEndToStart = swipeEndToStart,
                            onToggleEnabled = { enabled -> viewModel.toggleEnabled(rule, enabled) },
                            onToggleSelectOrExpand = {
                                if (hasSelection) viewModel.toggleSelection(rule.id)
                                else viewModel.toggleCardExpansion(rule.id)
                            },
                            onLongClick = {
                                viewModel.toggleSelection(rule.id)
                            },
                            onEdit = { onEditRule(rule.id) },
                            onDelete = { pendingDeleteRule = rule },
                            onDuplicate = { viewModel.duplicateRule(rule) },
                            onRunRule = { viewModel.runRule(rule) },
                            onCancelManualRun = { viewModel.cancelManualRun() },
                            showInlineProgressCancel = showInlineProgressCancel,
                            onPreviewRule = { viewModel.startPreview(rule) },
                            onViewHistory = { onNavigateToRuleHistory(rule.id) },
                            onLeadingLongClick = if (reorderLongPressActive) {
                                { viewModel.toggleSelection(rule.id) }
                            } else {
                                null
                            },
                            reorderLongPressDragModifier = reorderLongPressModifier,
                            suppressLongClickForReorder = reorderLongPressActive,
                            modifier = Modifier.shadow(dragElevation, RoundedCornerShape(16.dp))
                        )
                    }
                }
            }
        }
    }

    pendingDeleteRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRule = null },
            title = { Text("Delete rule?") },
            text = { Text("\"${rule.name}\" and its schedule will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    playTap()
                    viewModel.deleteRule(rule)
                    pendingDeleteRule = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    playTap()
                    pendingDeleteRule = null
                }) { Text("Cancel") }
            }
        )
    }

    if (pendingDeleteSelected) {
        val count = selectedRuleIds.size
        AlertDialog(
            onDismissRequest = { pendingDeleteSelected = false },
            title = { Text("Delete $count rule${if (count == 1) "" else "s"}?") },
            text = { Text("This will also remove any scheduled runs for the selected rules.") },
            confirmButton = {
                TextButton(onClick = {
                    playTap()
                    viewModel.deleteSelected()
                    pendingDeleteSelected = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    playTap()
                    pendingDeleteSelected = false
                }) { Text("Cancel") }
            }
        )
    }

    previewState?.let { preview ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissPreview() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Preview — ${preview.ruleName}",
                    style = MaterialTheme.typography.titleMedium
                )
                if (preview.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(32.dp)
                    )
                } else if (preview.results.isEmpty()) {
                    Text(
                        text = "No files would be affected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Text(
                        text = "${preview.results.size} file(s) would be affected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(
                            items = preview.results,
                            key = { previewItem -> previewItem.sourcePath }
                        ) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(result.fileName, style = MaterialTheme.typography.bodySmall)
                                    val status = when {
                                        result.wouldSkip -> "Would skip"
                                        result.renamedTo != null -> "→ ${result.renamedTo}"
                                        else -> "→ ${result.simulatedDestPath.substringAfterLast('/')}"
                                    }
                                    Text(
                                        status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val sizeKb = result.sizeBytes / 1024
                                Text(
                                    if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}


@Composable
private fun RulesSortDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sortKey: HistorySortKey,
    sortDirection: HistorySortDirection,
    onSelect: (HistorySortKey, HistorySortDirection) -> Unit
) {
    data class SortOption(val labelRes: Int, val key: HistorySortKey, val direction: HistorySortDirection)
    val options = listOf(
        SortOption(R.string.history_sort_last_ran_newest, HistorySortKey.LAST_RAN, HistorySortDirection.DESCENDING),
        SortOption(R.string.history_sort_last_ran_oldest, HistorySortKey.LAST_RAN, HistorySortDirection.ASCENDING),
        SortOption(R.string.history_sort_rule_name_az, HistorySortKey.RULE_NAME, HistorySortDirection.ASCENDING),
        SortOption(R.string.history_sort_rule_name_za, HistorySortKey.RULE_NAME, HistorySortDirection.DESCENDING),
        SortOption(R.string.rules_sort_my_order, HistorySortKey.MY_ORDER, HistorySortDirection.ASCENDING),
    )
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        options.forEach { option ->
            val isSelected = if (option.key == HistorySortKey.MY_ORDER)
                sortKey == HistorySortKey.MY_ORDER
            else
                sortKey == option.key && sortDirection == option.direction
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes)) },
                leadingIcon = { RadioButton(selected = isSelected, onClick = null) },
                onClick = { onSelect(option.key, option.direction) }
            )
        }
    }
}

@Composable
private fun SwipeToDismissRuleCard(
    rule: Rule,
    isSelected: Boolean,
    isExpanded: Boolean,
    progress: dev.bikram.filepipe.domain.model.RunProgress?,
    isAnyRuleRunning: Boolean,
    hasStaleFolder: Boolean,
    onStaleWarningClick: () -> Unit,
    swipeStartToEnd: SwipeAction,
    swipeEndToStart: SwipeAction,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleSelectOrExpand: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRunRule: () -> Unit,
    onCancelManualRun: () -> Unit,
    showInlineProgressCancel: Boolean,
    onPreviewRule: () -> Unit,
    onViewHistory: () -> Unit,
    onLeadingLongClick: (() -> Unit)? = null,
    reorderLongPressDragModifier: Modifier = Modifier,
    suppressLongClickForReorder: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(16.dp)
    val swipeAssigned = setOf(swipeStartToEnd, swipeEndToStart)
    val cardIconPairs: List<RuleCardAction> = SwipeAction.entries
        .filter { it !in swipeAssigned }
        .map { action ->
            RuleCardAction(
                icon = action.icon(),
                label = action.label(),
                onClick = { action.dispatch(onDelete, onEdit, onDuplicate, onViewHistory, onPreviewRule) }
            )
        }

    val hapticEnabled = LocalHapticEnabled.current
    DeliberateSwipeRevealCard(
        commitThresholdFraction = SwipeDismissCardDefaults.CommitThresholdFraction,
        cardShape = cardShape,
        onSwipeStartToEnd = {
            swipeStartToEnd.dispatch(onDelete, onEdit, onDuplicate, onViewHistory, onPreviewRule)
        },
        onSwipeEndToStart = {
            swipeEndToStart.dispatch(onDelete, onEdit, onDuplicate, onViewHistory, onPreviewRule)
        },
        hapticEnabled = hapticEnabled,
        backgroundContent = { fromStart ->
            val action = if (fromStart) swipeStartToEnd else swipeEndToStart
            Box(
                Modifier
                    .fillMaxSize()
                    .background(action.semanticSwipeBackground(), cardShape)
                    .padding(
                        start = if (fromStart) 24.dp else 0.dp,
                        end = if (fromStart) 0.dp else 24.dp
                    ),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = action.icon(),
                    contentDescription = null,
                    tint = action.semanticSwipeIconTint(),
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        modifier = modifier
    ) {
        RuleCard(
            rule = rule,
            isSelected = isSelected,
            isExpanded = isExpanded,
            progress = progress,
            onClick = onToggleSelectOrExpand,
            onLongClick = onLongClick,
            cardActions = cardIconPairs,
            onToggleEnabled = onToggleEnabled,
            onRunClick = onRunRule,
            onCancelRunClick = onCancelManualRun,
            showInlineProgressCancel = showInlineProgressCancel,
            isAnyRuleRunning = isAnyRuleRunning,
            onPreviewRule = onPreviewRule,
            onViewHistory = onViewHistory,
            hasStaleFolder = hasStaleFolder,
            onStaleWarningClick = onStaleWarningClick,
            onLeadingLongClick = onLeadingLongClick,
            reorderLongPressDragModifier = reorderLongPressDragModifier,
            suppressLongClickForReorder = suppressLongClickForReorder
        )
    }
}

private fun SwipeAction.dispatch(
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onViewHistory: () -> Unit,
    onPreview: () -> Unit
) = when (this) {
    SwipeAction.EDIT -> onEdit()
    SwipeAction.DELETE -> onDelete()
    SwipeAction.DUPLICATE -> onDuplicate()
    SwipeAction.PREVIEW -> onPreview()
    SwipeAction.VIEW_HISTORY -> onViewHistory()
}

private fun SwipeAction.icon(): ImageVector = when (this) {
    SwipeAction.EDIT -> Icons.Default.Edit
    SwipeAction.DELETE -> Icons.Default.Delete
    SwipeAction.DUPLICATE -> Icons.Default.ContentCopy
    SwipeAction.PREVIEW -> Icons.Default.Visibility
    SwipeAction.VIEW_HISTORY -> Icons.Default.History
}

@Composable
private fun SwipeAction.label(): String = when (this) {
    SwipeAction.EDIT -> stringResource(R.string.edit_rule)
    SwipeAction.DELETE -> stringResource(R.string.delete_rule)
    SwipeAction.DUPLICATE -> stringResource(R.string.duplicate_rule)
    SwipeAction.PREVIEW -> stringResource(R.string.preview_rule)
    SwipeAction.VIEW_HISTORY -> stringResource(R.string.view_history)
}

@Composable
private fun EmptyState(
    onAddRule: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
    ) {
        Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ThemeColoredEmptyRulesIllustration(Modifier.size(120.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.rules_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.rules_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAddRule,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(0.72f)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.rules_add_rule))
            }
        }
    }
}
