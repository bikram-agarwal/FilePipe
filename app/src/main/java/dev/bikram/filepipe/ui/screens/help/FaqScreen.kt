package dev.bikram.filepipe.ui.screens.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.filepipe.R
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.common.rememberHoistedStringTextFieldState
import dev.bikram.filepipe.ui.components.FilePipeButton
import dev.bikram.filepipe.ui.components.FilePipeFilledTonalButton
import dev.bikram.filepipe.ui.components.FilePipeFilledTonalIconButton
import dev.bikram.filepipe.ui.components.FilePipeIconButton
import dev.bikram.filepipe.ui.components.containers.GroupPosition
import dev.bikram.filepipe.ui.components.containers.GroupedListItem
import dev.bikram.filepipe.ui.feedback.appClickable
import dev.bikram.filepipe.ui.modifiers.progressiveBlurFullBleedLayer
import dev.bikram.filepipe.ui.navigation.Screen
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.gradientOverlayTopAppBarColors
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private const val SECTION_STORAGE_ACCESS_MODES = "storage_access_modes"
private const val ITEM_REGEX_FILTERS = "regex_filters"
private const val ITEM_PATTERNS = "include_exclude_patterns"

/**
 * What opening the FAQ at a focus key ([Screen.Faq.createRoute]) shows: the items to expand, and the
 * row to bring to the top - an item, or a whole section by its header. One entry per deep-link
 * target, the same shape as Remember's `helpFocusSubsectionTitles`, so a new link is a table row
 * rather than another branch in the screen.
 */
private data class FaqFocusTarget(
    val expandItemIds: List<String>,
    val scrollToItemId: String? = null,
    val scrollToSectionId: String? = null,
)

private val regexFocusTarget = FaqFocusTarget(listOf(ITEM_REGEX_FILTERS), scrollToItemId = ITEM_REGEX_FILTERS)

private val faqFocusTargets: Map<String, FaqFocusTarget> =
    mapOf(
        Screen.Faq.FOCUS_STORAGE_ACCESS to
            FaqFocusTarget(
                expandItemIds = listOf("storage_all_files", "storage_selective"),
                scrollToSectionId = SECTION_STORAGE_ACCESS_MODES,
            ),
        Screen.Faq.FOCUS_REGEX to regexFocusTarget,
        // Also accepted by its item id, as before this table.
        ITEM_REGEX_FILTERS to regexFocusTarget,
        Screen.Faq.FOCUS_PATTERNS to FaqFocusTarget(listOf(ITEM_PATTERNS), scrollToItemId = ITEM_PATTERNS),
    )

/**
 * HELP.md's inline markdown as the FAQ shows it: `**bold**` and `` `code` ``. Code is styled like
 * inline code in Remember's MarkdownText (monospace on surfaceVariant at 70%), which renders
 * Remember's Help; FilePipe's FAQ needs only these two inline forms, not that whole engine.
 */
@Composable
private fun faqInlineText(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    return remember(text, codeBackground) { parseFaqInlineMarkdown(text, codeBackground) }
}

/**
 * Inside a code span everything is literal (a `**` there stays as typed). A backtick with no closing
 * one is shown as typed; an unclosed `**` bolds to the end of the line, as before code spans existed.
 */
internal fun parseFaqInlineMarkdown(
    text: String,
    codeBackground: Color,
): AnnotatedString =
    buildAnnotatedString {
        var bold = false
        var index = 0
        while (index < text.length) {
            when {
                text[index] == '`' && text.indexOf('`', index + 1) > index -> {
                    val close = text.indexOf('`', index + 1)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(text, index + 1, close)
                    }
                    index = close + 1
                }

                text.startsWith("**", index) -> {
                    if (bold) pop() else pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    bold = !bold
                    index += 2
                }

                else -> {
                    append(text[index])
                    index++
                }
            }
        }
        if (bold) pop()
    }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FaqTopicListItem(
    itemContent: FaqItemContent,
    groupPosition: GroupPosition,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpenFolderAccessInSettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    spatialSpec: FiniteAnimationSpec<IntSize>,
    fadeInSpec: FiniteAnimationSpec<Float>,
    fadeOutSpec: FiniteAnimationSpec<Float>,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec()),
        label = "faq_chevron_rotation",
    )
    val chevronContainerSize by animateDpAsState(
        targetValue = if (isExpanded) 20.dp else 32.dp,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec()),
        label = "faq_chevron_container_size",
    )
    val chevronContainerColor by animateColorAsState(
        targetValue = if (isExpanded) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec()),
        label = "faq_chevron_container_color",
    )

    GroupedListItem(position = groupPosition) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .appClickable { onToggle() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = faqInlineText(itemContent.question),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier =
                        Modifier
                            .size(chevronContainerSize)
                            .clip(MaterialTheme.shapes.extraExtraLarge)
                            .background(chevronContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    FilePipeMaterialRoundedSymbol(
                        name = "chevron_right",
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
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
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemContent.body.forEachIndexed { index, line ->
                        // Non-bullet lines start a new group (sub-heading / label like "Use this to:"),
                        // so give them extra breathing room above so groups read as distinct on a quick scan.
                        val topSpacing = if (index > 0 && !line.isBullet) 10.dp else 0.dp
                        if (line.isHeading) {
                            Text(
                                text = faqInlineText(line.text),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = topSpacing),
                            )
                        } else {
                            Text(
                                text =
                                    buildAnnotatedString {
                                        if (line.isBullet) append("• ")
                                        append(faqInlineText(line.text))
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = topSpacing),
                            )
                        }
                    }
                    if (itemContent.inlineActions.isNotEmpty()) {
                        val actionOrder =
                            listOf(
                                FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS,
                                FaqInlineAction.OPEN_APP_NOTIFICATION_SETTINGS,
                            ).filter { ordered ->
                                ordered in itemContent.inlineActions
                            }
                        FlowRow(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            actionOrder.forEach { action ->
                                when (action) {
                                    FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS -> {
                                        FilePipeFilledTonalButton(
                                            onClick = onOpenFolderAccessInSettings,
                                        ) {
                                            Text(
                                                stringResource(
                                                    R.string.faq_action_switch_full_access,
                                                ),
                                            )
                                        }
                                    }

                                    FaqInlineAction.OPEN_APP_NOTIFICATION_SETTINGS -> {
                                        FilePipeFilledTonalButton(
                                            onClick = onOpenAppNotificationSettings,
                                        ) {
                                            Text(
                                                stringResource(
                                                    R.string.faq_action_notification_settings,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun computeLazyIndexForSectionHeader(
    focusSectionId: String,
    filteredSections: List<FaqSectionContent>,
): Int {
    var index = 1
    for (section in filteredSections) {
        if (section.sectionId == focusSectionId) return index
        index += 1
        if (section.showNotSureBanner) index += 1
        if (section.calloutBody != null) index += 1
        index += section.items.size
    }
    return -1
}

private fun computeLazyIndexForItem(
    focusItemId: String,
    filteredSections: List<FaqSectionContent>,
): Int {
    var index = 1
    for (section in filteredSections) {
        index += 1
        if (section.showNotSureBanner) index += 1
        if (section.calloutBody != null) index += 1
        for (item in section.items) {
            if (item.id == focusItemId) return index
            index += 1
        }
    }
    return -1
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun FaqScreen(
    initialFocusSectionId: String,
    onNavigateBack: () -> Unit,
    onOpenFolderAccessInSettings: () -> Unit,
    onOpenSettingsNotifications: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
) {
    val latestFolderAccess by rememberUpdatedState(onOpenFolderAccessInSettings)
    val latestNotifications by rememberUpdatedState(onOpenSettingsNotifications)
    val faqViewModel: FaqViewModel = hiltViewModel()
    val expandedItemIds by faqViewModel.expandedItemIds.collectAsStateWithLifecycle()
    val scrollPosition by faqViewModel.scrollPosition.collectAsStateWithLifecycle()
    val searchQuery by faqViewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredSections by faqViewModel.filteredSections.collectAsStateWithLifecycle()
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = scrollPosition.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = scrollPosition.firstVisibleItemScrollOffset,
        )
    val density = LocalDensity.current
    val topAlphaMultiplier by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offsetPx = listState.firstVisibleItemScrollOffset.toFloat()
                val thresholdPx = with(density) { 24.dp.toPx() }
                (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }
    val fullBleedBlurModifier =
        LocalProgressiveBlurStyle.current?.let { blurStyle ->
            Modifier.progressiveBlurFullBleedLayer(blurStyle, topAlphaMultiplier = topAlphaMultiplier)
        } ?: Modifier
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec<IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val allExpandableItemIds =
        remember(faqViewModel.sections) {
            faqViewModel.sections.flatMap { section -> section.items.map { item -> item.id } }
        }

    val allItemsExpanded =
        remember(expandedItemIds, allExpandableItemIds) {
            allExpandableItemIds.isNotEmpty() &&
                allExpandableItemIds.all { itemId -> itemId in expandedItemIds }
        }

    LaunchedEffect(listState) {
        snapshotFlow {
            FaqScrollPosition(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }.distinctUntilChanged()
            .collect { position -> faqViewModel.setScrollPosition(position) }
    }

    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Apply the focus once per visit: the flag is saveable, so rotating or searching afterwards
    // does not re-expand the section and jump back to it. Set only once the scroll is done, so a
    // run cancelled midway is retried. Remember's HelpScreen guards its focus the same way.
    // Unlike Remember's HelpScreen there is no old search to clear first: this screen's query lives
    // in FaqViewModel (not FaqUiStateStore), so every visit starts unfiltered. And the top bar here
    // doesn't collapse, so there's no app-bar offset to sync after the jump either.
    var focusHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialFocusSectionId, filteredSections) {
        if (focusHandled || filteredSections.isEmpty()) return@LaunchedEffect
        faqFocusTargets[initialFocusSectionId]?.let { target ->
            target.expandItemIds
                .filterNot { itemId -> itemId in expandedItemIds }
                .forEach { itemId -> faqViewModel.setItemExpanded(itemId, true) }
            val scrollIndex =
                when {
                    target.scrollToSectionId != null -> {
                        computeLazyIndexForSectionHeader(target.scrollToSectionId, filteredSections)
                    }

                    target.scrollToItemId != null -> {
                        computeLazyIndexForItem(target.scrollToItemId, filteredSections)
                    }

                    else -> {
                        -1
                    }
                }
            if (scrollIndex >= 0) {
                delay(120)
                listState.scrollToItem(scrollIndex)
            }
        }
        focusHandled = true
    }

    val scheme = MaterialTheme.colorScheme
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (LocalUseGradientBackground.current) {
                        Modifier
                            .background(scheme.surface)
                            .background(
                                Brush.verticalGradient(
                                    colorStops =
                                        arrayOf(
                                            0f to scheme.primaryContainer.copy(alpha = 0.45f),
                                            0.55f to scheme.surface.copy(alpha = 0f),
                                        ),
                                ),
                            )
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.background)
                    },
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(fullBleedBlurModifier),
        ) {
            if (LocalUseGradientBackground.current) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scheme.surface)
                        .background(
                            Brush.verticalGradient(
                                colorStops =
                                    arrayOf(
                                        0f to scheme.primaryContainer.copy(alpha = 0.45f),
                                        0.55f to scheme.surface.copy(alpha = 0f),
                                    ),
                            ),
                        ),
                )
            } else {
                Box(Modifier.fillMaxSize().background(scheme.background))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = statusTop + 64.dp,
                        bottom = navBottom + 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilePipeButton(
                                    onClick = latestFolderAccess,
                                ) {
                                    FilePipeMaterialRoundedSymbol(
                                        name = "folder_open",
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                    Text(stringResource(R.string.faq_quick_action_folder_access))
                                }
                                FilePipeButton(
                                    onClick = latestNotifications,
                                ) {
                                    FilePipeMaterialRoundedSymbol(
                                        name = "notifications",
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                    Text(stringResource(R.string.faq_quick_action_notifications))
                                }
                            }
                        }
                        val searchFieldState =
                            rememberHoistedStringTextFieldState(
                                text = searchQuery,
                                onTextChange = faqViewModel::setSearchQuery,
                            )
                        OutlinedTextField(
                            state = searchFieldState,
                            modifier = Modifier.fillMaxWidth(),
                            lineLimits = TextFieldLineLimits.SingleLine,
                            shape = MaterialTheme.shapes.extraExtraLarge,
                            placeholder = { Text(stringResource(R.string.faq_search_hint)) },
                            leadingIcon = {
                                FilePipeMaterialRoundedSymbol(
                                    name = "search",
                                    contentDescription = null,
                                )
                            },
                            trailingIcon =
                                if (searchQuery.isNotBlank()) {
                                    {
                                        FilePipeIconButton(onClick = { faqViewModel.setSearchQuery("") }) {
                                            FilePipeMaterialRoundedSymbol(
                                                name = "close",
                                                contentDescription = stringResource(R.string.faq_clear_search),
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                }

                if (filteredSections.isEmpty()) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.faq_no_results_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.faq_no_results_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    filteredSections.forEach { sectionContent ->
                        item(key = "section_${sectionContent.sectionId}") {
                            Text(
                                text = sectionContent.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 20.dp, bottom = 6.dp, start = 4.dp),
                            )
                        }
                        if (sectionContent.showNotSureBanner) {
                            item(key = "not_sure_${sectionContent.sectionId}") {
                                Surface(
                                    modifier = Modifier.padding(bottom = 10.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.onboarding_permissions_sheet_footer_tip),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }
                        sectionContent.calloutBody?.let { calloutText ->
                            item(key = "callout_${sectionContent.sectionId}") {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                ) {
                                    Text(
                                        text = faqInlineText(calloutText),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        itemsIndexed(
                            items = sectionContent.items,
                            key = { _, itemContent -> itemContent.id },
                        ) { index, itemContent ->
                            val isExpanded = itemContent.id in expandedItemIds
                            val groupPosition =
                                when {
                                    sectionContent.items.size == 1 -> GroupPosition.ONLY
                                    index == 0 -> GroupPosition.FIRST
                                    index == sectionContent.items.lastIndex -> GroupPosition.LAST
                                    else -> GroupPosition.MIDDLE
                                }
                            FaqTopicListItem(
                                itemContent = itemContent,
                                groupPosition = groupPosition,
                                isExpanded = isExpanded,
                                onToggle = {
                                    faqViewModel.setItemExpanded(itemContent.id, !isExpanded)
                                },
                                onOpenFolderAccessInSettings = onOpenFolderAccessInSettings,
                                onOpenAppNotificationSettings = onOpenAppNotificationSettings,
                                spatialSpec = spatialSpec,
                                fadeInSpec = fadeInSpec,
                                fadeOutSpec = fadeOutSpec,
                            )
                        }
                    }
                }
            }
        }

        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = { Text(stringResource(R.string.faq_title)) },
            navigationIcon = {
                FilePipeIconButton(onClick = onNavigateBack) {
                    FilePipeMaterialRoundedSymbol(
                        name = "arrow_back",
                        contentDescription = stringResource(R.string.nav_back),
                        autoMirror = true,
                    )
                }
            },
            actions = {
                FilePipeFilledTonalIconButton(
                    onClick = {
                        if (allItemsExpanded) {
                            faqViewModel.collapseAll()
                        } else {
                            faqViewModel.expandAll(allExpandableItemIds)
                        }
                    },
                ) {
                    FilePipeMaterialRoundedSymbol(
                        name = if (allItemsExpanded) "unfold_less" else "unfold_more",
                        contentDescription =
                            if (allItemsExpanded) {
                                stringResource(R.string.faq_collapse_all_cd)
                            } else {
                                stringResource(R.string.faq_expand_all_cd)
                            },
                    )
                }
            },
            colors = gradientOverlayTopAppBarColors(),
        )
    }
}
