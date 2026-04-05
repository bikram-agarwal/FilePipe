package dev.bikram.filepipe.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey
import dev.bikram.filepipe.domain.model.HistoryStatusFilter
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.ui.components.HistoryCard
import dev.bikram.filepipe.ui.components.ThemeColoredEmptyHistoryIllustration
import dev.bikram.filepipe.ui.feedback.LocalHapticEnabled
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound
import dev.bikram.filepipe.ui.components.DeliberateSwipeRevealCard
import dev.bikram.filepipe.ui.components.SwipeDismissCardDefaults
import dev.bikram.filepipe.ui.modifiers.progressiveBlur
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onHistoryClick: (Long) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val playTap = rememberPlayTapSound()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagingItems = viewModel.historyPagingFlow.collectAsLazyPagingItems()
    val filteredItems by viewModel.filteredHistoryItems.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    var showClearConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isFiltered = viewModel.filterRuleId != null

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }

    val isInitialLoad = pagingItems.loadState.refresh is LoadState.Loading
    val isUsingPaging = !uiState.isFilterActive
    val isEmpty = if (isUsingPaging) {
        !isInitialLoad && pagingItems.itemCount == 0
    } else {
        filteredItems.isEmpty()
    }

    LaunchedEffect(userMessage) {
        val msg = userMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearUserMessage()
    }

    val filterChipsData = listOf(
        HistoryStatusFilter.ALL to stringResource(R.string.history_filter_all),
        HistoryStatusFilter.SUCCESS to stringResource(R.string.status_success),
        HistoryStatusFilter.FAILED to stringResource(R.string.status_failed),
        HistoryStatusFilter.PARTIAL to stringResource(R.string.status_partial),
        HistoryStatusFilter.NO_CHANGES to stringResource(R.string.status_no_changes),
        HistoryStatusFilter.CANCELLED to stringResource(R.string.status_cancelled),
        HistoryStatusFilter.UNDONE to stringResource(R.string.status_undone)
    )

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        playTap()
                        showClearConfirm = false
                        viewModel.clearAllHistory()
                    }
                ) {
                    Text(stringResource(R.string.history_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    playTap()
                    showClearConfirm = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = if (LocalUseGradientBackground.current) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            val appBarColors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
            val navigationIcon: @Composable () -> Unit = {
                if (onNavigateBack != null) {
                    IconButton(onClick = {
                        playTap()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            }
            Column(Modifier.fillMaxWidth()) {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.history_title)) },
                    scrollBehavior = scrollBehavior,
                    colors = appBarColors,
                    navigationIcon = navigationIcon,
                    actions = {
                        Box {
                            FilledTonalIconButton(onClick = {
                                playTap()
                                groupMenuExpanded = true
                            }) {
                                val groupIcon = when (uiState.viewMode) {
                                    HistoryViewMode.BY_DATE -> Icons.Default.DateRange
                                    HistoryViewMode.BY_RULE -> Icons.AutoMirrored.Filled.List
                                    HistoryViewMode.BY_STATUS -> Icons.Default.Category
                                }
                                Icon(
                                    imageVector = groupIcon,
                                    contentDescription = stringResource(R.string.history_group_menu)
                                )
                            }
                            DropdownMenu(
                                expanded = groupMenuExpanded,
                                onDismissRequest = { groupMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_group_by_date)) },
                                    onClick = {
                                        playTap()
                                        viewModel.setViewMode(HistoryViewMode.BY_DATE)
                                        groupMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_group_by_rule)) },
                                    onClick = {
                                        playTap()
                                        viewModel.setViewMode(HistoryViewMode.BY_RULE)
                                        groupMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_group_by_status)) },
                                    onClick = {
                                        playTap()
                                        viewModel.setViewMode(HistoryViewMode.BY_STATUS)
                                        groupMenuExpanded = false
                                    }
                                )
                            }
                        }
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
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_sort_last_ran_newest)) },
                                    onClick = {
                                        playTap()
                                        viewModel.setSort(HistorySortKey.LAST_RAN, HistorySortDirection.DESCENDING)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_sort_last_ran_oldest)) },
                                    onClick = {
                                        playTap()
                                        viewModel.setSort(HistorySortKey.LAST_RAN, HistorySortDirection.ASCENDING)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_sort_rule_name_az)) },
                                    onClick = {
                                        playTap()
                                        viewModel.setSort(HistorySortKey.RULE_NAME, HistorySortDirection.ASCENDING)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_sort_rule_name_za)) },
                                    onClick = {
                                        playTap()
                                        viewModel.setSort(HistorySortKey.RULE_NAME, HistorySortDirection.DESCENDING)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = filterChipsData,
                        key = { (filter, _) -> filter.ordinal }
                    ) { (filter, label) ->
                        FilterChip(
                            selected = uiState.statusFilter == filter,
                            onClick = { playTap(); viewModel.setStatusFilter(filter) },
                            label = { Text(label) },
                            leadingIcon = if (uiState.statusFilter == filter) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val blurStyle = LocalProgressiveBlurStyle.current
        val scrollBlurModifier = if (blurStyle != null) {
            Modifier.progressiveBlur(
                blurRadius = blurStyle.blurRadius,
                topHeight = blurStyle.topHeightPx,
                bottomHeight = blurStyle.bottomHeightPx,
                showGradientOverlay = true,
                overlayAlpha = blurStyle.overlayAlpha,
            )
        } else {
            Modifier
        }

        if (isEmpty) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(scrollBlurModifier)
                        .padding(top = innerPadding.calculateTopPadding())
                        .padding(bottom = contentPadding.calculateBottomPadding())
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ThemeColoredEmptyHistoryIllustration(Modifier.size(120.dp))
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.history_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.history_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (isUsingPaging) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(scrollBlurModifier),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isFiltered) {
                    item(key = "filter_header") {
                        Text(
                            text = "Showing runs for this rule only",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { item ->
                        when (item) {
                            is HistoryItem.Entry -> "entry_${item.history.id}"
                            is HistoryItem.DateHeader -> "header_${item.label}"
                            is HistoryItem.RuleHeader -> "rule_${item.ruleName}"
                            is HistoryItem.StatusHeader -> "status_${item.section.name}"
                        }
                    }
                ) { index ->
                    when (val item = pagingItems[index]) {
                        is HistoryItem.DateHeader -> {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        is HistoryItem.RuleHeader -> Unit
                        is HistoryItem.StatusHeader -> Unit
                        is HistoryItem.Entry -> {
                            SwipeToDismissHistoryCard(
                                history = item.history,
                                onClick = { playTap(); onHistoryClick(item.history.id) },
                                onDelete = { viewModel.deleteHistoryEntry(item.history.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                        null -> Unit
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(scrollBlurModifier),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    }
                ) { item ->
                    when (item) {
                        is HistoryItem.DateHeader -> {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp).animateItem()
                            )
                        }
                        is HistoryItem.RuleHeader -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateItem(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.ruleName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${item.count}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is HistoryItem.StatusHeader -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateItem(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = historyStatusSectionTitle(item.section),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${item.count}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is HistoryItem.Entry -> {
                            SwipeToDismissHistoryCard(
                                history = item.history,
                                onClick = { playTap(); onHistoryClick(item.history.id) },
                                onDelete = { viewModel.deleteHistoryEntry(item.history.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun historyStatusSectionTitle(section: HistoryStatusSection): String = when (section) {
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
    modifier: Modifier = Modifier
) {
    val hapticEnabled = LocalHapticEnabled.current
    val cardShape = RoundedCornerShape(12.dp)
    DeliberateSwipeRevealCard(
        commitThresholdFraction = SwipeDismissCardDefaults.CommitThresholdFraction,
        cardShape = cardShape,
        onSwipeStartToEnd = { },
        onSwipeEndToStart = onDelete,
        hapticEnabled = hapticEnabled,
        allowSwipeStartToEnd = false,
        allowSwipeEndToStart = true,
        backgroundContent = { fromStart ->
            if (!fromStart) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.32f),
                            cardShape
                        )
                        .padding(end = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) {
        HistoryCard(history = history, onClick = onClick)
    }
}
