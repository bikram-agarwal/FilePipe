package dev.bikram.filepipe.ui.screens.rules

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.data.preferences.materialSymbolName
import dev.bikram.filepipe.devtools.DevMockFileMove
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.ui.common.AppBottomSheet
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.components.DeliberateSwipeRevealCard
import dev.bikram.filepipe.ui.components.FilePipeButton
import dev.bikram.filepipe.ui.components.FilePipeConfirmDialog
import dev.bikram.filepipe.ui.components.FilePipeDropdownMenuItem
import dev.bikram.filepipe.ui.components.FilePipeFilledTonalButton
import dev.bikram.filepipe.ui.components.FilePipeFilledTonalIconButton
import dev.bikram.filepipe.ui.components.FilePipeOutlinedButton
import dev.bikram.filepipe.ui.components.FilePipeConfirmDialog
import dev.bikram.filepipe.ui.components.FilePipeTextButton
import dev.bikram.filepipe.ui.components.RuleCard
import dev.bikram.filepipe.ui.components.RuleCardAction
import dev.bikram.filepipe.ui.components.RuleCardFolderIssueSeverity
import dev.bikram.filepipe.ui.components.SwipeDismissCardDefaults
import dev.bikram.filepipe.ui.components.ThemeColoredEmptyRulesIllustration
import dev.bikram.filepipe.ui.components.displayPath
import dev.bikram.filepipe.ui.components.previewSourceFolderDisplayPath
import dev.bikram.filepipe.ui.feedback.LocalHapticEnabled
import dev.bikram.filepipe.ui.modifiers.progressiveBlurScrollableList
import dev.bikram.filepipe.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.filepipe.ui.navigation.Screen
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalSnackbarHostState
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.gradientOverlayTopAppBarColors
import dev.bikram.filepipe.ui.theme.pillShape
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import dev.bikram.filepipe.ui.theme.reducedMotionEnterTransition
import dev.bikram.filepipe.ui.theme.reducedMotionExitTransition
import dev.bikram.filepipe.ui.theme.semanticSwipeBackground
import dev.bikram.filepipe.ui.theme.semanticSwipeIconTint
import kotlinx.coroutines.flow.first
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RulesScreen(
    contentPadding: PaddingValues,
    onEditRule: (Long) -> Unit,
    onNavigateToHistoryDetail: (Long) -> Unit,
    onNavigateToHistoryList: () -> Unit,
    onNavigateToRuleHistory: (Long) -> Unit,
    activeRuleId: Long? = null,
    onActivateRuleInDetailPane: ((Long) -> Unit)? = null,
    onActivateRuleForRunInDetailPane: ((Long) -> Unit)? = null,
    showPendingNewRuleInDetailPane: Boolean = false,
    showSelectionActionBar: Boolean = true,
    listStartPadding: Dp = 16.dp,
    listEndPadding: Dp = 16.dp,
    viewModel: RulesViewModel = hiltViewModel(),
) {
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
    val staleRuleWarningIds = uiState.staleRuleWarningIds
    val staleRuleErrorIds = uiState.staleRuleErrorIds
    val previewState = uiState.previewState
    val context = LocalContext.current
    val resources = LocalResources.current

    var pendingDeleteSelected by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = LocalSnackbarHostState.current
    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
    val hasSelection = selectedRuleIds.isNotEmpty()
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val topAlphaMultiplier by remember(lazyListState) {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offsetPx = lazyListState.firstVisibleItemScrollOffset.toFloat()
                val thresholdPx = with(density) { 24.dp.toPx() }
                (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }
    val scrollBlurModifier =
        LocalProgressiveBlurStyle.current?.let { blurStyle ->
            Modifier.progressiveBlurScrollableList(blurStyle, topAlphaMultiplier = topAlphaMultiplier)
        } ?: Modifier
    val listScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = lazyListState,
            additionalScrollEnabled = false,
            ignoredBottomPadding = 56.dp,
        )
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
    val reorderableLazyListState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            dragActuallyMoved = true
            reorderableRules =
                reorderableRules.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
        }
    val sortKeyState = rememberUpdatedState(sortKey)

    LaunchedEffect(showPendingNewRuleInDetailPane) {
        if (showPendingNewRuleInDetailPane) {
            lazyListState.animateScrollToItem(0)
        }
    }

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
            val label =
                if (count == 1) {
                    resources.getString(R.string.rule_moved_to_trash, event.rules.first().name)
                } else {
                    resources.getQuantityString(R.plurals.rules_moved_to_trash, count, count)
                }
            val result =
                snackbarHostState.showSnackbar(
                    message = label,
                    actionLabel = resources.getString(R.string.undo),
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(event.rules)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userMessages.collect { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short,
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel, snackbarHostState) {
        val observer =
            LifecycleEventObserver { _, event ->
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
        containerColor = if (LocalUseGradientBackground.current) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {},
                    colors = gradientOverlayTopAppBarColors(),
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (showSelectionActionBar && hasSelection && !isRunning) {
                                Box(modifier = Modifier.size(48.dp)) {
                                    val selectAllLabel = stringResource(R.string.run_select_all)
                                    FilePipeFilledTonalIconButton(
                                        onClick = { viewModel.selectAll() },
                                        modifier = Modifier.align(Alignment.Center),
                                        tooltipLabel = selectAllLabel,
                                    ) {
                                        FilePipeMaterialRoundedSymbol(
                                            name = "select_all",
                                            contentDescription = selectAllLabel,
                                        )
                                    }
                                    Badge(
                                        modifier =
                                            Modifier
                                                .align(Alignment.BottomStart)
                                                .offset(x = 2.dp, y = (-2).dp),
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ) {
                                        Text(
                                            text = selectedRuleIds.size.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                                val deselectAllLabel = stringResource(R.string.run_deselect_all)
                                FilePipeFilledTonalIconButton(
                                    onClick = { viewModel.clearSelection() },
                                    tooltipLabel = deselectAllLabel,
                                    colors =
                                        IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                ) {
                                    FilePipeMaterialRoundedSymbol(name = "deselect", contentDescription = deselectAllLabel)
                                }
                            } else {
                                Box(modifier = Modifier.size(48.dp)) {
                                    val sortLabel = stringResource(R.string.history_sort_menu)
                                    FilePipeFilledTonalIconButton(
                                        onClick = { sortMenuExpanded = true },
                                        modifier = Modifier.align(Alignment.Center),
                                        tooltipLabel = sortLabel,
                                    ) {
                                        FilePipeMaterialRoundedSymbol(
                                            name = "sort",
                                            contentDescription = sortLabel,
                                            autoMirror = true,
                                        )
                                    }
                                    RulesSortDropdown(
                                        expanded = sortMenuExpanded,
                                        onDismiss = { sortMenuExpanded = false },
                                        sortKey = sortKey,
                                        sortDirection = sortDirection,
                                        onSelect = { key, direction ->
                                            viewModel.setSort(key, direction)
                                            sortMenuExpanded = false
                                        },
                                    )
                                }
                                val expandCollapseLabel =
                                    if (isCompactMode) {
                                        stringResource(R.string.rules_expand_all)
                                    } else {
                                        stringResource(R.string.rules_collapse_all)
                                    }
                                FilePipeFilledTonalIconButton(
                                    onClick = { viewModel.toggleGlobalViewMode() },
                                    tooltipLabel = expandCollapseLabel,
                                ) {
                                    FilePipeMaterialRoundedSymbol(
                                        name = if (isCompactMode) "unfold_more" else "unfold_less",
                                        contentDescription = expandCollapseLabel,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            when {
                showSelectionActionBar && isRunning && manualRunCancelAnchor == ManualRunCancelAnchor.RunSelectedBar -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = pillShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 3.dp,
                            shadowElevation = 3.dp,
                        ) {
                            FilePipeOutlinedButton(
                                onClick = { viewModel.cancelManualRun() },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = pillShape,
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }

                isRunning -> { }

                else -> {
                    val selectionBarSpatialSpec =
                        reducedMotionAwareSpec(
                            MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>(),
                        )
                    val selectionBarFadeInSpec =
                        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
                    val selectionBarFadeOutSpec =
                        reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
                    AnimatedVisibility(
                        visible = showSelectionActionBar && hasSelection,
                        enter =
                            reducedMotionEnterTransition(
                                expandVertically(animationSpec = selectionBarSpatialSpec) +
                                    fadeIn(animationSpec = selectionBarFadeInSpec),
                            ),
                        exit =
                            reducedMotionExitTransition(
                                shrinkVertically(animationSpec = selectionBarSpatialSpec) +
                                    fadeOut(animationSpec = selectionBarFadeOutSpec),
                            ),
                    ) {
                        val enabledSelectedCount = rules.count { it.id in selectedRuleIds && it.isEnabled }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = pillShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 3.dp,
                                shadowElevation = 3.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val cancelSelectionLabel = stringResource(R.string.run_cancel_selection)
                                    FilePipeFilledTonalIconButton(
                                        onClick = { viewModel.clearSelection() },
                                        tooltipLabel = cancelSelectionLabel,
                                        colors =
                                            IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                    ) {
                                        FilePipeMaterialRoundedSymbol(name = "close", contentDescription = cancelSelectionLabel)
                                    }
                                    val deleteLabel = stringResource(R.string.delete)
                                    FilePipeFilledTonalIconButton(
                                        onClick = { pendingDeleteSelected = true },
                                        tooltipLabel = deleteLabel,
                                        colors =
                                            IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                            ),
                                    ) {
                                        FilePipeMaterialRoundedSymbol(name = "delete", contentDescription = deleteLabel)
                                    }
                                    val previewSelectedLabel = stringResource(R.string.preview_selected_rules)
                                    FilePipeFilledTonalIconButton(
                                        onClick = { viewModel.startPreviewSelected() },
                                        tooltipLabel = previewSelectedLabel,
                                    ) {
                                        FilePipeMaterialRoundedSymbol(
                                            name = "visibility",
                                            contentDescription = previewSelectedLabel,
                                        )
                                    }
                                    FilePipeFilledTonalButton(
                                        onClick = { viewModel.runSelected() },
                                        enabled = enabledSelectedCount > 0,
                                        shape = pillShape,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.run_button),
                                            style = MaterialTheme.typography.labelLarge.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                            modifier = Modifier.align(Alignment.CenterVertically),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        FilePipeMaterialRoundedSymbol(
                                            name = "play_arrow",
                                            contentDescription = null,
                                            size = 18.dp,
                                            modifier = Modifier.offset(x = 1.dp).align(Alignment.CenterVertically),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (rules.isEmpty() && !showPendingNewRuleInDetailPane) {
            RulesEmptyState(
                onAddRule = {
                    onEditRule(Screen.RuleDetail.NEW_RULE_ID)
                },
                modifier =
                    Modifier
                        .fillMaxSize(),
                // Clearances ride inside the scroll container (see RulesEmptyState) so
                // they never become a hard wall that clips the empty state.
                contentPadding =
                    PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom =
                            innerPadding.calculateBottomPadding() +
                                contentPadding.calculateBottomPadding(),
                    ),
                showAddRuleAction = showSelectionActionBar,
            )
        } else {
            val reorderLongPressActive = !isRunning
            val bottomChromePadding =
                maxOf(
                    innerPadding.calculateBottomPadding(),
                    contentPadding.calculateBottomPadding(),
                )
            val listColumnPadding =
                PaddingValues(
                    start = listStartPadding,
                    end = listEndPadding,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = bottomChromePadding + 56.dp,
                )
            val listModifier =
                Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = 720.dp)
                    .then(scrollBlurModifier)

            LazyColumn(
                state = lazyListState,
                modifier = listModifier,
                contentPadding = listColumnPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = listScrollEnabled,
            ) {
                if (showPendingNewRuleInDetailPane) {
                    item(key = "pending_new_rule") {
                        PendingNewRuleCard(
                            onClick = {
                                onActivateRuleInDetailPane?.invoke(Screen.RuleDetail.NEW_RULE_ID)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                items(reorderableRules, key = { it.id }) { rule ->
                    ReorderableItem(
                        reorderableLazyListState,
                        rule.id,
                        modifier = Modifier.animateItem(),
                    ) { isDragging ->
                        var suppressExpandAfterReorderLongPress by remember(rule.id) { mutableStateOf(false) }
                        val dragElevation by animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 0.dp,
                            animationSpec =
                                reducedMotionAwareSpec(
                                    MaterialTheme.motionScheme.defaultSpatialSpec(),
                                ),
                            label = "ruleCardReorderShadow",
                        )
                        val reorderLongPressModifier =
                            if (reorderLongPressActive) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = { _ ->
                                        dragActuallyMoved = false
                                        suppressExpandAfterReorderLongPress = true
                                    },
                                    onDragStopped = {
                                        if (!dragActuallyMoved) {
                                            viewModel.toggleSelection(rule.id)
                                        } else {
                                            viewModel.applyDraggedOrder(
                                                reorderableRules,
                                                alsoSwitchSortToMyOrder = sortKeyState.value != HistorySortKey.MY_ORDER,
                                            )
                                        }
                                        dragActuallyMoved = false
                                    },
                                )
                            } else {
                                Modifier
                            }
                        val isExpanded =
                            viewModel.isCardExpanded(rule.id, isCompactMode, cardModeOverrides)
                        val showInlineProgressCancel =
                            manualRunCancelAnchor is ManualRunCancelAnchor.SingleRule &&
                                manualRunCancelAnchor.ruleId == rule.id
                        val folderIssueSeverity =
                            when (rule.id) {
                                in staleRuleErrorIds -> RuleCardFolderIssueSeverity.ERROR
                                in staleRuleWarningIds -> RuleCardFolderIssueSeverity.WARNING
                                else -> null
                            }
                        SwipeToDismissRuleCard(
                            rule = rule,
                            isSelected = rule.id in selectedRuleIds,
                            isActiveInDetailPane = !hasSelection && rule.id == activeRuleId,
                            isSelectionMode = hasSelection,
                            isExpanded = isExpanded,
                            progress = progressMap[rule.id],
                            isAnyRuleRunning = isRunning,
                            hasStaleFolder = rule.id in staleRuleIds,
                            folderIssueSeverity = folderIssueSeverity,
                            onStaleWarningClick = { onEditRule(rule.id) },
                            swipeStartToEnd = swipeStartToEnd,
                            swipeEndToStart = swipeEndToStart,
                            onToggleEnabled = { enabled -> viewModel.toggleEnabled(rule, enabled) },
                            onToggleSelectOrExpand = {
                                if (suppressExpandAfterReorderLongPress) {
                                    suppressExpandAfterReorderLongPress = false
                                } else if (hasSelection) {
                                    viewModel.toggleSelection(rule.id)
                                } else {
                                    onEditRule(rule.id)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(rule.id)
                            },
                            onToggleExpansion = { viewModel.toggleCardExpansion(rule.id) },
                            onDelete = { viewModel.deleteRule(rule) },
                            onDuplicate = { viewModel.duplicateRule(rule) },
                            onRunRule = {
                                onActivateRuleForRunInDetailPane?.invoke(rule.id)
                                viewModel.runRule(rule)
                            },
                            onCancelManualRun = { viewModel.cancelManualRun() },
                            showInlineProgressCancel = showInlineProgressCancel,
                            onPreviewRule = { viewModel.startPreview(rule) },
                            onViewHistory = { onNavigateToRuleHistory(rule.id) },
                            onLeadingLongClick =
                                if (reorderLongPressActive) {
                                    { viewModel.toggleSelection(rule.id) }
                                } else {
                                    null
                                },
                            reorderLongPressDrag = { reorderLongPressModifier },
                            suppressLongClickForReorder = reorderLongPressActive,
                            modifier = Modifier.shadow(dragElevation, MaterialTheme.shapes.medium),
                        )
                    }
                }
            }
        }
    }

    if (pendingDeleteSelected) {
        val count = selectedRuleIds.size
        FilePipeConfirmDialog(
            title = pluralStringResource(R.plurals.rules_move_to_trash_confirm_title, count, count),
            text = stringResource(R.string.rules_move_to_trash_confirm_message),
            confirmLabel = stringResource(R.string.move_to_trash),
            onConfirm = {
                viewModel.deleteSelected()
                pendingDeleteSelected = false
            },
            onDismiss = { pendingDeleteSelected = false },
            destructive = true,
        )
    }

    previewState?.let { preview ->
        val previewRunEnabled =
            !isRunning &&
                !preview.isLoading &&
                preview.ruleGroups.any { ruleGroup ->
                    ruleGroup.results.any { result -> !result.wouldSkip } &&
                        rules.any { rule -> rule.id == ruleGroup.ruleId && rule.isEnabled }
                }
        val previewTitle =
            preview.selectedRuleCount?.let { selectedRuleCount ->
                pluralStringResource(
                    R.plurals.preview_title_selected_rules,
                    selectedRuleCount,
                    selectedRuleCount,
                )
            } ?: stringResource(R.string.preview_title_for_rule, preview.ruleName)
        AppBottomSheet(
            title = previewTitle,
            onDismiss = { viewModel.dismissPreview() },
            sheetState =
                rememberBottomSheetState(
                    initialValue = SheetValue.Hidden,
                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                ),
            scrollable = false,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (preview.isLoading) {
                    LoadingIndicator(
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(32.dp)
                                .size(48.dp),
                    )
                } else if (preview.ruleGroups.all { it.results.isEmpty() }) {
                    Text(
                        text = stringResource(R.string.preview_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                    ) {
                        preview.ruleGroups.forEach { ruleGroup ->
                            item(key = "header_${ruleGroup.ruleId}") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = ruleGroup.ruleName,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text =
                                            pluralStringResource(
                                                when (ruleGroup.operationMode) {
                                                    OperationMode.MOVE -> R.plurals.preview_files_would_move
                                                    OperationMode.COPY -> R.plurals.preview_files_would_copy
                                                },
                                                ruleGroup.results.size,
                                                ruleGroup.results.size,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            val groupedResults =
                                ruleGroup.results.groupBy { result ->
                                    previewSourceFolderDisplayPath(
                                        sourcePath = result.sourcePath,
                                        fileName = result.fileName,
                                        internalStorageRootDisplayName = internalStorageDisplayName,
                                    )
                                }
                            groupedResults.forEach { (sourceFolder, sourceFiles) ->
                                item(key = "source_${ruleGroup.ruleId}_$sourceFolder") {
                                    Text(
                                        text = sourceFolder.trimEnd('/') + "/",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                items(
                                    items = sourceFiles,
                                    key = { previewItem -> "${ruleGroup.ruleId}_${previewItem.sourcePath}" },
                                ) { result ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = result.fileName,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            when {
                                                result.wouldSkip -> {
                                                    Text(
                                                        text = stringResource(R.string.preview_would_skip),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }

                                                result.wouldOverwrite -> {
                                                    Text(
                                                        text = stringResource(R.string.preview_would_overwrite),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }

                                                result.renamedTo != null -> {
                                                    Text(
                                                        text =
                                                            stringResource(
                                                                R.string.preview_destination_path,
                                                                displayPath(result.simulatedDestPath, internalStorageDisplayName),
                                                            ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                        val sizeKb = result.sizeBytes / 1024
                                        Text(
                                            if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilePipeTextButton(onClick = { viewModel.dismissPreview() }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    FilePipeButton(
                        onClick = { viewModel.runPreviewedRules() },
                        enabled = previewRunEnabled,
                    ) {
                        Text(stringResource(R.string.preview_run))
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
    onSelect: (HistorySortKey, HistorySortDirection) -> Unit,
) {
    data class SortOption(
        val labelRes: Int,
        val key: HistorySortKey,
        val direction: HistorySortDirection,
    )
    val options =
        listOf(
            SortOption(R.string.history_sort_last_ran_newest, HistorySortKey.LAST_RAN, HistorySortDirection.DESCENDING),
            SortOption(R.string.history_sort_last_ran_oldest, HistorySortKey.LAST_RAN, HistorySortDirection.ASCENDING),
            SortOption(R.string.history_sort_rule_name_az, HistorySortKey.RULE_NAME, HistorySortDirection.ASCENDING),
            SortOption(R.string.history_sort_rule_name_za, HistorySortKey.RULE_NAME, HistorySortDirection.DESCENDING),
            SortOption(R.string.rules_sort_my_order, HistorySortKey.MY_ORDER, HistorySortDirection.ASCENDING),
        )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        options.forEach { option ->
            val isSelected =
                if (option.key == HistorySortKey.MY_ORDER) {
                    sortKey == HistorySortKey.MY_ORDER
                } else {
                    sortKey == option.key && sortDirection == option.direction
                }
            FilePipeDropdownMenuItem(
                text = { Text(stringResource(option.labelRes)) },
                leadingIcon = { RadioButton(selected = isSelected, onClick = null) },
                onClick = { onSelect(option.key, option.direction) },
            )
        }
    }
}

@Composable
private fun SwipeToDismissRuleCard(
    rule: Rule,
    isSelected: Boolean,
    isActiveInDetailPane: Boolean,
    isSelectionMode: Boolean,
    isExpanded: Boolean,
    progress: dev.bikram.filepipe.domain.model.RunProgress?,
    isAnyRuleRunning: Boolean,
    hasStaleFolder: Boolean,
    folderIssueSeverity: RuleCardFolderIssueSeverity?,
    onStaleWarningClick: () -> Unit,
    swipeStartToEnd: SwipeAction,
    swipeEndToStart: SwipeAction,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleSelectOrExpand: () -> Unit,
    onLongClick: () -> Unit,
    onToggleExpansion: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRunRule: () -> Unit,
    onCancelManualRun: () -> Unit,
    showInlineProgressCancel: Boolean,
    onPreviewRule: () -> Unit,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onLeadingLongClick: (() -> Unit)? = null,
    reorderLongPressDrag: () -> Modifier = { Modifier },
    suppressLongClickForReorder: Boolean = false,
) {
    val cardShape = MaterialTheme.shapes.medium
    val isMockRule = DevMockFileMove.isMockRule(rule)
    val swipeAssigned = setOf(swipeStartToEnd, swipeEndToStart)
    val cardIconPairs: List<RuleCardAction> =
        SwipeAction.entries
            .filter { it !in swipeAssigned }
            .filterNot { action -> isMockRule && action.isBlockedForMockRule() }
            .map { action ->
                RuleCardAction(
                    iconName = action.materialSymbolName(isExpanded),
                    label = action.label(isExpanded),
                    onClick = {
                        action.dispatch(
                            onDelete = onDelete,
                            onToggleExpansion = onToggleExpansion,
                            onDuplicate = onDuplicate,
                            onViewHistory = onViewHistory,
                            onPreview = onPreviewRule,
                        )
                    },
                )
            }

    val hapticEnabled = LocalHapticEnabled.current
    DeliberateSwipeRevealCard(
        commitThresholdFraction = SwipeDismissCardDefaults.COMMIT_THRESHOLD_FRACTION,
        cardShape = cardShape,
        onSwipeStartToEnd = {
            if (!isMockRule || !swipeStartToEnd.isBlockedForMockRule()) {
                swipeStartToEnd.dispatch(
                    onDelete = onDelete,
                    onToggleExpansion = onToggleExpansion,
                    onDuplicate = onDuplicate,
                    onViewHistory = onViewHistory,
                    onPreview = onPreviewRule,
                )
            }
        },
        onSwipeEndToStart = {
            if (!isMockRule || !swipeEndToStart.isBlockedForMockRule()) {
                swipeEndToStart.dispatch(
                    onDelete = onDelete,
                    onToggleExpansion = onToggleExpansion,
                    onDuplicate = onDuplicate,
                    onViewHistory = onViewHistory,
                    onPreview = onPreviewRule,
                )
            }
        },
        hapticEnabled = hapticEnabled,
        allowSwipeStartToEnd = !isMockRule || !swipeStartToEnd.isBlockedForMockRule(),
        allowSwipeEndToStart = !isMockRule || !swipeEndToStart.isBlockedForMockRule(),
        backgroundContent = { fromStart, revealProgress ->
            val action = if (fromStart) swipeStartToEnd else swipeEndToStart
            val backgroundColor by animateColorAsState(
                targetValue = action.semanticSwipeBackground(),
                animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec()),
                label = "ruleSwipeBg",
            )
            val iconTint = action.semanticSwipeIconTint()
            val contentScale = 0.88f + 0.12f * revealProgress
            Box(
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor, cardShape)
                    .padding(
                        start = if (fromStart) 16.dp else 0.dp,
                        end = if (fromStart) 0.dp else 16.dp,
                    ),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = revealProgress
                            scaleX = contentScale
                            scaleY = contentScale
                        },
                ) {
                    if (fromStart) {
                        FilePipeMaterialRoundedSymbol(
                            name = action.materialSymbolName(isExpanded),
                            contentDescription = null,
                            size = 20.dp,
                            tint = iconTint,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = action.shortLabel(isExpanded),
                            style = MaterialTheme.typography.labelMedium,
                            color = iconTint,
                        )
                    } else {
                        Text(
                            text = action.shortLabel(isExpanded),
                            style = MaterialTheme.typography.labelMedium,
                            color = iconTint,
                        )
                        Spacer(Modifier.width(6.dp))
                        FilePipeMaterialRoundedSymbol(
                            name = action.materialSymbolName(isExpanded),
                            contentDescription = null,
                            size = 20.dp,
                            tint = iconTint,
                        )
                    }
                }
            }
        },
        modifier = modifier,
    ) {
        RuleCard(
            rule = rule,
            isSelected = isSelected,
            isActiveInDetailPane = isActiveInDetailPane,
            isSelectionMode = isSelectionMode,
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
            hasStaleFolder = hasStaleFolder,
            folderIssueSeverity = folderIssueSeverity,
            onStaleWarningClick = onStaleWarningClick,
            onLeadingLongClick = onLeadingLongClick,
            reorderLongPressDrag = reorderLongPressDrag,
            suppressLongClickForReorder = suppressLongClickForReorder,
        )
    }
}

@Composable
private fun PendingNewRuleCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendingRule =
        remember {
            Rule(
                id = Screen.RuleDetail.NEW_RULE_ID,
                name = "",
                sourceFolderPaths = emptyList(),
                destinationFolderPath = "",
                fileExtensions = emptyList(),
                isEnabled = true,
            )
        }
    RuleCard(
        rule = pendingRule.copy(name = stringResource(R.string.new_rule)),
        isSelected = false,
        isActiveInDetailPane = true,
        isSelectionMode = false,
        isExpanded = false,
        progress = null,
        onClick = onClick,
        onLongClick = {},
        cardActions = emptyList(),
        onToggleEnabled = {},
        onRunClick = {},
        onCancelRunClick = {},
        isAnyRuleRunning = false,
        showOperationalControls = false,
        modifier = modifier,
    )
}

private fun SwipeAction.isBlockedForMockRule(): Boolean = this == SwipeAction.DUPLICATE

private fun SwipeAction.materialSymbolName(isExpanded: Boolean): String =
    when (this) {
        SwipeAction.EDIT -> if (isExpanded) "unfold_less" else "unfold_more"
        else -> materialSymbolName()
    }

private fun SwipeAction.dispatch(
    onDelete: () -> Unit,
    onToggleExpansion: () -> Unit,
    onDuplicate: () -> Unit,
    onViewHistory: () -> Unit,
    onPreview: () -> Unit,
) = when (this) {
    SwipeAction.EDIT -> onToggleExpansion()
    SwipeAction.DELETE -> onDelete()
    SwipeAction.DUPLICATE -> onDuplicate()
    SwipeAction.PREVIEW -> onPreview()
    SwipeAction.VIEW_HISTORY -> onViewHistory()
}

@Composable
private fun SwipeAction.label(isExpanded: Boolean): String =
    when (this) {
        SwipeAction.EDIT -> {
            stringResource(
                if (isExpanded) {
                    R.string.action_collapse
                } else {
                    R.string.action_expand
                },
            )
        }

        SwipeAction.DELETE -> {
            stringResource(R.string.delete_rule)
        }

        SwipeAction.DUPLICATE -> {
            stringResource(R.string.duplicate_rule)
        }

        SwipeAction.PREVIEW -> {
            stringResource(R.string.preview_rule)
        }

        SwipeAction.VIEW_HISTORY -> {
            stringResource(R.string.view_history)
        }
    }

@Composable
private fun SwipeAction.shortLabel(isExpanded: Boolean): String =
    when (this) {
        SwipeAction.EDIT -> {
            stringResource(
                if (isExpanded) {
                    R.string.action_collapse
                } else {
                    R.string.action_expand
                },
            )
        }

        SwipeAction.DELETE -> {
            stringResource(R.string.settings_swipe_action_trash)
        }

        SwipeAction.DUPLICATE -> {
            stringResource(R.string.action_duplicate)
        }

        SwipeAction.PREVIEW -> {
            stringResource(R.string.preview_title)
        }

        SwipeAction.VIEW_HISTORY -> {
            stringResource(R.string.settings_swipe_action_history)
        }
    }

@Composable
fun RulesEmptyState(
    onAddRule: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    // Pane mode hosts its own add-rule FAB in the list pane, so the inline button
    // is redundant there.
    showAddRuleAction: Boolean = true,
) {
    val emptyStateSpatialSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
    val emptyStateFadeSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    AnimatedVisibility(
        visible = true,
        enter =
            reducedMotionEnterTransition(
                fadeIn(animationSpec = emptyStateFadeSpec) +
                    slideInVertically(animationSpec = emptyStateSpatialSpec) { it / 4 },
            ),
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                // Scroll spans the whole pane; the host clearances and margins live
                // inside it so low-height panes scroll edge-to-edge instead of
                // clipping the illustration/text at a padded boundary.
                modifier =
                    Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding)
                        .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ThemeColoredEmptyRulesIllustration(Modifier.size(120.dp))
                Spacer(Modifier.height(24.dp))
                Column(
                    modifier = Modifier.semantics(mergeDescendants = true) { },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.rules_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.rules_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                    )
                }
                if (showAddRuleAction) {
                    Spacer(Modifier.height(24.dp))
                    FilePipeButton(
                        onClick = onAddRule,
                        shape = pillShape,
                        modifier = Modifier.fillMaxWidth(0.72f),
                    ) {
                        FilePipeMaterialRoundedSymbol(
                            name = "add",
                            contentDescription = null,
                            size = 20.dp,
                            opticalCenterYOffset = (-2).dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.rules_add_rule))
                    }
                }
            }
        }
    }
}
