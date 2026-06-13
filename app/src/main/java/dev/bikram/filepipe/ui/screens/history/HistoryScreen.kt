package dev.bikram.filepipe.ui.screens.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey
import dev.bikram.filepipe.domain.model.HistoryStatusFilter
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.common.isSmallLandscape
import dev.bikram.filepipe.ui.components.DeliberateSwipeRevealCard
import dev.bikram.filepipe.ui.components.FilePipeDropdownMenuItem
import dev.bikram.filepipe.ui.components.FilePipeFilledTonalIconButton
import dev.bikram.filepipe.ui.components.FilePipeFilterChip
import dev.bikram.filepipe.ui.components.FilePipeIconButton
import dev.bikram.filepipe.ui.components.FilePipeConfirmDialog
import dev.bikram.filepipe.ui.components.FilePipeToggleButton
import dev.bikram.filepipe.ui.components.HistoryCard
import dev.bikram.filepipe.ui.components.RuleCard
import dev.bikram.filepipe.ui.components.SwipeDismissCardDefaults
import dev.bikram.filepipe.ui.components.ThemeColoredEmptyHistoryIllustration
import dev.bikram.filepipe.ui.components.ThemeColoredEmptyTrashIllustration
import dev.bikram.filepipe.ui.feedback.LocalHapticEnabled
import dev.bikram.filepipe.ui.modifiers.progressiveBlurScrollableList
import dev.bikram.filepipe.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalReducedMotion
import dev.bikram.filepipe.ui.theme.LocalSnackbarHostState
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.gradientOverlayTopAppBarColors
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import dev.bikram.filepipe.ui.theme.reducedMotionEnterTransition
import dev.bikram.filepipe.ui.theme.semanticSwipeBackground
import dev.bikram.filepipe.ui.theme.semanticSwipeIconTint
import kotlin.math.ceil

private const val TRASH_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onHistoryClick: (Long) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    activeHistoryId: Long? = null,
    onActivateTrashedRuleInDetailPane: ((Long) -> Unit)? = null,
    listStartPadding: Dp = 16.dp,
    listEndPadding: Dp = 16.dp,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val availableStatusFilters by viewModel.availableStatusFilters.collectAsStateWithLifecycle()
    val trashedRules by viewModel.trashedRules.collectAsStateWithLifecycle()
    val pagingItems = viewModel.historyPagingFlow.collectAsLazyPagingItems()
    val filteredItems by viewModel.filteredHistoryItems.collectAsStateWithLifecycle()
    val hasAnyHistory by viewModel.hasAnyHistory.collectAsStateWithLifecycle()
    val isSmallLandscape = isSmallLandscape()

    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingDeleteForeverRule by remember { mutableStateOf<Rule?>(null) }
    var expandedTrashRuleIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val snackbarHostState = LocalSnackbarHostState.current
    val isFiltered = viewModel.filterRuleId != null

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }

    val isInitialLoad = pagingItems.loadState.refresh is LoadState.Loading
    val isUsingPaging = !uiState.isFilterActive
    val reducedMotion = LocalReducedMotion.current
    val pagingListState = rememberLazyListState()
    val filteredListState = rememberLazyListState()
    val trashListState = rememberLazyListState()
    val pagingListScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = pagingListState,
        )
    val filteredListScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = filteredListState,
        )
    val trashListScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = trashListState,
        )

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        viewModel.userMessages.collect { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short,
            )
        }
    }
    DisposableEffect(lifecycleOwner, snackbarHostState) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filterChipsData =
        listOf(
            HistoryStatusFilter.ALL to stringResource(R.string.history_filter_all),
            HistoryStatusFilter.SUCCESS to stringResource(R.string.status_success),
            HistoryStatusFilter.FAILED to stringResource(R.string.status_failed),
            HistoryStatusFilter.PARTIAL to stringResource(R.string.status_partial),
            HistoryStatusFilter.NO_CHANGES to stringResource(R.string.status_no_changes),
            HistoryStatusFilter.CANCELLED to stringResource(R.string.status_cancelled),
            HistoryStatusFilter.UNDONE to stringResource(R.string.status_undone),
        )

    if (showClearConfirm) {
        FilePipeConfirmDialog(
            title = stringResource(R.string.history_clear_confirm_title),
            text = stringResource(R.string.history_clear_confirm_message),
            confirmLabel = stringResource(R.string.history_clear),
            onConfirm = {
                showClearConfirm = false
                viewModel.clearAllHistory()
            },
            onDismiss = { showClearConfirm = false },
            destructive = true,
        )
    }
    pendingDeleteForeverRule?.let { rule ->
        FilePipeConfirmDialog(
            title = stringResource(R.string.history_trash_delete_forever_confirm_title),
            text = stringResource(R.string.history_trash_delete_forever_confirm_message, rule.name),
            confirmLabel = stringResource(R.string.delete_forever),
            onConfirm = {
                pendingDeleteForeverRule = null
                viewModel.deleteRuleForever(rule.id)
            },
            onDismiss = { pendingDeleteForeverRule = null },
            destructive = true,
        )
    }

    Scaffold(
        containerColor = if (LocalUseGradientBackground.current) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            val showTopBar = !(isSmallLandscape && !hasAnyHistory)
            val navigationIcon: @Composable () -> Unit = {
                if (onNavigateBack != null) {
                    val backLabel = stringResource(R.string.nav_back)
                    FilePipeIconButton(
                        onClick = onNavigateBack,
                        tooltipLabel = backLabel,
                    ) {
                        FilePipeMaterialRoundedSymbol(
                            name = "arrow_back",
                            contentDescription = backLabel,
                            autoMirror = true,
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth()) {
                if (showTopBar) {
                    TopAppBar(
                        title = {},
                        colors = gradientOverlayTopAppBarColors(),
                        navigationIcon = navigationIcon,
                        actions = {
                            if (section == HistorySection.RUNS) {
                                Box {
                                    val groupMenuLabel = stringResource(R.string.history_group_menu)
                                    FilePipeFilledTonalIconButton(
                                        onClick = { groupMenuExpanded = true },
                                        enabled = hasAnyHistory,
                                        tooltipLabel = groupMenuLabel,
                                    ) {
                                        val groupIcon =
                                            when (uiState.viewMode) {
                                                HistoryViewMode.BY_DATE -> "calendar_month"
                                                HistoryViewMode.BY_RULE -> "list"
                                                HistoryViewMode.BY_STATUS -> "category"
                                            }
                                        FilePipeMaterialRoundedSymbol(
                                            name = groupIcon,
                                            contentDescription = groupMenuLabel,
                                            autoMirror = uiState.viewMode == HistoryViewMode.BY_RULE,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = groupMenuExpanded,
                                        onDismissRequest = { groupMenuExpanded = false },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ) {
                                        FilePipeDropdownMenuItem(
                                            text = { Text(stringResource(R.string.history_group_by_date)) },
                                            leadingIcon = { RadioButton(selected = uiState.viewMode == HistoryViewMode.BY_DATE, onClick = null) },
                                            onClick = {
                                                viewModel.setViewMode(HistoryViewMode.BY_DATE)
                                                groupMenuExpanded = false
                                            },
                                        )
                                        FilePipeDropdownMenuItem(
                                            text = { Text(stringResource(R.string.history_group_by_rule)) },
                                            leadingIcon = { RadioButton(selected = uiState.viewMode == HistoryViewMode.BY_RULE, onClick = null) },
                                            onClick = {
                                                viewModel.setViewMode(HistoryViewMode.BY_RULE)
                                                groupMenuExpanded = false
                                            },
                                        )
                                        FilePipeDropdownMenuItem(
                                            text = { Text(stringResource(R.string.history_group_by_status)) },
                                            leadingIcon = { RadioButton(selected = uiState.viewMode == HistoryViewMode.BY_STATUS, onClick = null) },
                                            onClick = {
                                                viewModel.setViewMode(HistoryViewMode.BY_STATUS)
                                                groupMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                                Box {
                                    val sortMenuLabel = stringResource(R.string.history_sort_menu)
                                    FilePipeFilledTonalIconButton(
                                        onClick = { sortMenuExpanded = true },
                                        enabled = hasAnyHistory,
                                        tooltipLabel = sortMenuLabel,
                                    ) {
                                        FilePipeMaterialRoundedSymbol(
                                            name = "sort",
                                            contentDescription = sortMenuLabel,
                                            autoMirror = true,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = sortMenuExpanded,
                                        onDismissRequest = { sortMenuExpanded = false },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ) {
                                        FilePipeDropdownMenuItem(
                                            text = { Text(stringResource(R.string.history_sort_last_ran_newest)) },
                                            leadingIcon = { RadioButton(selected = uiState.sortKey == HistorySortKey.LAST_RAN && uiState.sortDirection == HistorySortDirection.DESCENDING, onClick = null) },
                                            onClick = {
                                                viewModel.setSort(HistorySortKey.LAST_RAN, HistorySortDirection.DESCENDING)
                                                sortMenuExpanded = false
                                            },
                                        )
                                        FilePipeDropdownMenuItem(
                                            text = { Text(stringResource(R.string.history_sort_last_ran_oldest)) },
                                            leadingIcon = { RadioButton(selected = uiState.sortKey == HistorySortKey.LAST_RAN && uiState.sortDirection == HistorySortDirection.ASCENDING, onClick = null) },
                                            onClick = {
                                                viewModel.setSort(HistorySortKey.LAST_RAN, HistorySortDirection.ASCENDING)
                                                sortMenuExpanded = false
                                            },
                                        )
                                        FilePipeDropdownMenuItem(
                                            text = { Text(stringResource(R.string.history_sort_rule_name_az)) },
                                            leadingIcon = { RadioButton(selected = uiState.sortKey == HistorySortKey.RULE_NAME && uiState.sortDirection == HistorySortDirection.ASCENDING, onClick = null) },
                                            onClick = {
                                                viewModel.setSort(HistorySortKey.RULE_NAME, HistorySortDirection.ASCENDING)
                                                sortMenuExpanded = false
                                            },
                                        )
                                        FilePipeDropdownMenuItem(
                                            text = { Text(stringResource(R.string.history_sort_rule_name_za)) },
                                            leadingIcon = { RadioButton(selected = uiState.sortKey == HistorySortKey.RULE_NAME && uiState.sortDirection == HistorySortDirection.DESCENDING, onClick = null) },
                                            onClick = {
                                                viewModel.setSort(HistorySortKey.RULE_NAME, HistorySortDirection.DESCENDING)
                                                sortMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
                if (!showTopBar && onNavigateBack != null) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 8.dp,
                                    end = listEndPadding,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val backLabel = stringResource(R.string.nav_back)
                        FilePipeIconButton(
                            onClick = onNavigateBack,
                            tooltipLabel = backLabel,
                        ) {
                            FilePipeMaterialRoundedSymbol(
                                name = "arrow_back",
                                contentDescription = backLabel,
                                autoMirror = true,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        HistorySectionSegmentedRow(
                            selected = section,
                            onSelect = { nextSection ->
                                viewModel.setSection(nextSection)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    HistorySectionSegmentedRow(
                        selected = section,
                        onSelect = { nextSection ->
                            viewModel.setSection(nextSection)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = listStartPadding,
                                    end = listEndPadding,
                                    top = if (!showTopBar) 16.dp else 0.dp,
                                ),
                    )
                }
                if (section == HistorySection.RUNS && hasAnyHistory) {
                    LazyRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = listStartPadding, end = listEndPadding, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = filterChipsData,
                            key = { (filter, _) -> filter.ordinal },
                        ) { (filter, label) ->
                            val filterEnabled = filter in availableStatusFilters
                            FilePipeFilterChip(
                                selected = uiState.statusFilter == filter,
                                onClick = {
                                    viewModel.setStatusFilter(filter)
                                },
                                enabled = filterEnabled,
                                label = { Text(label) },
                                leadingIcon =
                                    if (uiState.statusFilter == filter) {
                                        {
                                            FilePipeMaterialRoundedSymbol(
                                                name = "check",
                                                contentDescription = null,
                                                size = 16.dp,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val density = LocalDensity.current
        val activeListState =
            remember(section, isUsingPaging, trashListState, pagingListState, filteredListState) {
                when {
                    section == HistorySection.TRASH -> trashListState
                    isUsingPaging -> pagingListState
                    else -> filteredListState
                }
            }
        val topAlphaMultiplier by remember(activeListState) {
            derivedStateOf {
                if (activeListState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    val offsetPx = activeListState.firstVisibleItemScrollOffset.toFloat()
                    val thresholdPx = with(density) { 24.dp.toPx() }
                    (offsetPx / thresholdPx).coerceIn(0f, 1f)
                }
            }
        }
        val scrollBlurModifier =
            LocalProgressiveBlurStyle.current?.let { blurStyle ->
                Modifier.progressiveBlurScrollableList(blurStyle, topAlphaMultiplier = topAlphaMultiplier)
            } ?: Modifier

        val historySectionSpatialSpec =
            reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
        val historySectionFadeInSpec =
            reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
        val historySectionFadeOutSpec =
            reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

        AnimatedContent(
            targetState = section,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (reducedMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (
                        slideInHorizontally(animationSpec = historySectionSpatialSpec) { fullWidth ->
                            direction * fullWidth
                        } + fadeIn(animationSpec = historySectionFadeInSpec)
                    ) togetherWith (
                        slideOutHorizontally(animationSpec = historySectionSpatialSpec) { fullWidth ->
                            -direction * fullWidth / 3
                        } + fadeOut(animationSpec = historySectionFadeOutSpec)
                    )
                }.using(SizeTransform(clip = false))
            },
            label = "history_section_content",
        ) { targetSection ->
            val targetIsEmpty =
                if (targetSection == HistorySection.TRASH) {
                    trashedRules.isEmpty()
                } else if (isUsingPaging) {
                    !isInitialLoad && pagingItems.itemCount == 0
                } else {
                    filteredItems.isEmpty()
                }

            if (targetIsEmpty) {
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
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        // The scroll container spans the whole pane and the top/FAB
                        // clearances live INSIDE it — exactly like the content lists.
                        // Low-height panes then scroll edge-to-edge instead of clipping
                        // the empty state at a padded boundary.
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(top = innerPadding.calculateTopPadding())
                                .padding(bottom = contentPadding.calculateBottomPadding())
                                .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (targetSection == HistorySection.TRASH) {
                            ThemeColoredEmptyTrashIllustration()
                        } else {
                            ThemeColoredEmptyHistoryIllustration(Modifier.size(120.dp))
                        }
                        Spacer(Modifier.height(24.dp))
                        Column(
                            modifier = Modifier.semantics(mergeDescendants = true) { },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        if (targetSection == HistorySection.TRASH) {
                                            R.string.history_trash_empty_title
                                        } else {
                                            R.string.history_empty_title
                                        },
                                    ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text =
                                    stringResource(
                                        if (targetSection == HistorySection.TRASH) {
                                            R.string.history_trash_empty_subtitle
                                        } else {
                                            R.string.history_empty_subtitle
                                        },
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else if (targetSection == HistorySection.TRASH) {
                LazyColumn(
                    state = trashListState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 720.dp)
                            .then(scrollBlurModifier),
                    contentPadding =
                        PaddingValues(
                            start = listStartPadding,
                            end = listEndPadding,
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding(),
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = trashListScrollEnabled,
                ) {
                    item(key = "trash_retention_notice") {
                        RetentionNotice(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                        )
                    }
                    items(
                        items = trashedRules,
                        key = { rule -> "trash_rule_${rule.id}" },
                    ) { rule ->
                        SwipeToDismissTrashRuleCard(
                            rule = rule,
                            isExpanded = false,
                            onToggleExpanded = {
                                onActivateTrashedRuleInDetailPane?.invoke(rule.id)
                            },
                            onRestore = { viewModel.restoreRule(rule.id) },
                            onDeleteForever = { pendingDeleteForeverRule = rule },
                            isActiveInDetailPane = rule.id == activeHistoryId,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            } else if (isUsingPaging) {
                LazyColumn(
                    state = pagingListState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 720.dp)
                            .then(scrollBlurModifier),
                    contentPadding =
                        PaddingValues(
                            start = listStartPadding,
                            end = listEndPadding,
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding(),
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = pagingListScrollEnabled,
                ) {
                    if (isFiltered) {
                        item(key = "filter_header") {
                            Text(
                                text = stringResource(R.string.history_filter_rule_header),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    items(
                        count = pagingItems.itemCount,
                        key =
                            pagingItems.itemKey { item ->
                                when (item) {
                                    is HistoryItem.Entry -> "entry_${item.history.id}"
                                    is HistoryItem.DateHeader -> "header_${item.label}"
                                    is HistoryItem.RuleHeader -> "rule_${item.ruleName}"
                                    is HistoryItem.StatusHeader -> "status_${item.section.name}"
                                }
                            },
                    ) { index ->
                        when (val item = pagingItems[index]) {
                            is HistoryItem.DateHeader -> {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }

                            is HistoryItem.RuleHeader -> {}

                            is HistoryItem.StatusHeader -> {}

                            is HistoryItem.Entry -> {
                                SwipeToDismissHistoryCard(
                                    history = item.history,
                                    onClick = { onHistoryClick(item.history.id) },
                                    onDelete = { viewModel.deleteHistoryEntry(item.history.id) },
                                    isActiveInDetailPane = item.history.id == activeHistoryId,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            null -> {}
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = filteredListState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 720.dp)
                            .then(scrollBlurModifier),
                    contentPadding =
                        PaddingValues(
                            start = listStartPadding,
                            end = listEndPadding,
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding(),
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = filteredListScrollEnabled,
                ) {
                    items(
                        items = filteredItems,
                        key = { item ->
                            when (item) {
                                is HistoryItem.Entry -> "entry_${item.history.id}"
                                is HistoryItem.DateHeader -> "header_${item.label}"
                                is HistoryItem.RuleHeader -> "rule_${item.ruleName}"
                                is HistoryItem.StatusHeader -> "status_${item.section.name}"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is HistoryItem.DateHeader -> {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp).animateItem(),
                                )
                            }

                            is HistoryItem.RuleHeader -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateItem(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = item.ruleName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "${item.count}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            is HistoryItem.StatusHeader -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateItem(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = historyStatusSectionTitle(item.section),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "${item.count}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            is HistoryItem.Entry -> {
                                SwipeToDismissHistoryCard(
                                    history = item.history,
                                    onClick = { onHistoryClick(item.history.id) },
                                    onDelete = { viewModel.deleteHistoryEntry(item.history.id) },
                                    isActiveInDetailPane = item.history.id == activeHistoryId,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySectionSegmentedRow(
    selected: HistorySection,
    onSelect: (HistorySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    val entries = HistorySection.entries
    val labels =
        entries.map { section ->
            when (section) {
                HistorySection.RUNS -> stringResource(R.string.history_section_runs)
                HistorySection.TRASH -> stringResource(R.string.history_section_trash)
            }
        }
    val shapes =
        entries.mapIndexed { index, _ ->
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
        }
    ButtonGroup(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState = menuState) },
    ) {
        entries.forEachIndexed { index, entry ->
            val label = labels[index]
            customItem(
                buttonGroupContent = {
                    FilePipeToggleButton(
                        checked = selected == entry,
                        onCheckedChange = { checked -> if (checked) onSelect(entry) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                        shapes = shapes[index],
                        colors = colors,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                menuContent = { menuState ->
                    FilePipeDropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelect(entry)
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun RetentionNotice(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.large,
                ).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilePipeMaterialRoundedSymbol(
            name = "schedule",
            contentDescription = null,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.history_trash_retention_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwipeToDismissTrashRuleCard(
    rule: Rule,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
    isActiveInDetailPane: Boolean = false,
) {
    val hapticEnabled = LocalHapticEnabled.current
    val cardShape = MaterialTheme.shapes.medium
    DeliberateSwipeRevealCard(
        commitThresholdFraction = SwipeDismissCardDefaults.COMMIT_THRESHOLD_FRACTION,
        cardShape = cardShape,
        onSwipeStartToEnd = onRestore,
        onSwipeEndToStart = onDeleteForever,
        hapticEnabled = hapticEnabled,
        backgroundContent = { fromStart, revealProgress ->
            val action = if (fromStart) SwipeAction.PREVIEW else SwipeAction.DELETE
            val background by animateColorAsState(
                targetValue = action.semanticSwipeBackground(),
                animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec()),
                label = "trashSwipeBg",
            )
            val iconTint = action.semanticSwipeIconTint()
            val iconName = if (fromStart) "restore_from_trash" else "delete_forever"
            val visualLabel = stringResource(if (fromStart) R.string.restore else R.string.delete_forever)
            val contentDescription = stringResource(if (fromStart) R.string.history_trash_restore_rule else R.string.delete_forever)
            val contentScale = 0.88f + 0.12f * revealProgress
            Box(
                Modifier
                    .fillMaxSize()
                    .background(background, cardShape)
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
                            name = iconName,
                            contentDescription = contentDescription,
                            size = 20.dp,
                            tint = iconTint,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = visualLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = iconTint,
                        )
                    } else {
                        Text(
                            text = visualLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = iconTint,
                        )
                        Spacer(Modifier.width(6.dp))
                        FilePipeMaterialRoundedSymbol(
                            name = iconName,
                            contentDescription = contentDescription,
                            size = 20.dp,
                            tint = iconTint,
                        )
                    }
                }
            }
        },
        modifier = modifier,
    ) {
        TrashRuleCard(
            rule = rule,
            isExpanded = isExpanded,
            onToggleExpanded = onToggleExpanded,
            daysLeft = daysLeftInTrash(rule),
            isActiveInDetailPane = isActiveInDetailPane,
        )
    }
}

@Composable
private fun TrashRuleCard(
    rule: Rule,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    daysLeft: Int?,
    isActiveInDetailPane: Boolean = false,
) {
    Box(Modifier.fillMaxWidth()) {
        RuleCard(
            rule = rule,
            isSelected = false,
            isActiveInDetailPane = isActiveInDetailPane,
            isSelectionMode = false,
            isExpanded = isExpanded,
            progress = null,
            onClick = onToggleExpanded,
            onLongClick = {},
            cardActions = emptyList(),
            onToggleEnabled = {},
            onRunClick = {},
            onCancelRunClick = {},
            isAnyRuleRunning = false,
            suppressLongClickForReorder = true,
            showOperationalControls = false,
        )
        daysLeft?.let { value ->
            TrashDaysLeftBadge(
                daysLeft = value,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp),
            )
        }
    }
}

@Composable
private fun TrashDaysLeftBadge(
    daysLeft: Int,
    modifier: Modifier = Modifier,
) {
    val label =
        if (daysLeft <= 0) {
            stringResource(R.string.history_expires_today)
        } else {
            pluralStringResource(R.plurals.history_days_left, daysLeft, daysLeft)
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color =
            if (daysLeft <= 3) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier =
            modifier
                .background(
                    if (daysLeft <= 3) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    MaterialTheme.shapes.extraExtraLarge,
                ).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun daysLeftInTrash(rule: Rule): Int? {
    val trashedAt = rule.trashedAt ?: return null
    val remaining = trashedAt + TRASH_RETENTION_MILLIS - System.currentTimeMillis()
    return ceil(remaining.toDouble() / 86_400_000.0).toInt().coerceAtLeast(0)
}

@Composable
private fun historyStatusSectionTitle(section: HistoryStatusSection): String =
    when (section) {
        HistoryStatusSection.SUCCESS -> stringResource(R.string.status_success)
        HistoryStatusSection.FAILED -> stringResource(R.string.status_failed)
        HistoryStatusSection.PARTIAL -> stringResource(R.string.status_partial)
        HistoryStatusSection.NO_CHANGES -> stringResource(R.string.status_no_changes)
        HistoryStatusSection.IN_PROGRESS -> stringResource(R.string.history_status_header_in_progress)
        HistoryStatusSection.CANCELLED -> stringResource(R.string.status_cancelled)
        HistoryStatusSection.UNDONE -> stringResource(R.string.status_undone)
    }

@Composable
private fun SwipeToDismissHistoryCard(
    history: RunHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isActiveInDetailPane: Boolean = false,
) {
    val hapticEnabled = LocalHapticEnabled.current
    val cardShape = MaterialTheme.shapes.medium
    DeliberateSwipeRevealCard(
        commitThresholdFraction = SwipeDismissCardDefaults.COMMIT_THRESHOLD_FRACTION,
        cardShape = cardShape,
        onSwipeStartToEnd = { },
        onSwipeEndToStart = onDelete,
        hapticEnabled = hapticEnabled,
        allowSwipeStartToEnd = false,
        allowSwipeEndToStart = true,
        backgroundContent = { fromStart, revealProgress ->
            if (!fromStart) {
                val contentScale = 0.88f + 0.12f * revealProgress
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(SwipeAction.DELETE.semanticSwipeBackground(), cardShape)
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    alpha = revealProgress
                                    scaleX = contentScale
                                    scaleY = contentScale
                                },
                    ) {
                        Text(
                            text = stringResource(R.string.delete_forever),
                            style = MaterialTheme.typography.labelMedium,
                            color = SwipeAction.DELETE.semanticSwipeIconTint(),
                        )
                        Spacer(Modifier.width(6.dp))
                        FilePipeMaterialRoundedSymbol(
                            name = "delete_forever",
                            contentDescription = stringResource(R.string.delete_forever),
                            size = 20.dp,
                            tint = SwipeAction.DELETE.semanticSwipeIconTint(),
                        )
                    }
                }
            }
        },
        modifier = modifier,
    ) {
        HistoryCard(
            history = history,
            onClick = onClick,
            isActiveInDetailPane = isActiveInDetailPane,
        )
    }
}
