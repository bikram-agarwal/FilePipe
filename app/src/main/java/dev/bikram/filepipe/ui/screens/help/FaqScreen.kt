package dev.bikram.filepipe.ui.screens.help

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound
import dev.bikram.filepipe.ui.modifiers.applyToFullBleedLayer
import dev.bikram.filepipe.ui.navigation.Screen
import dev.bikram.filepipe.ui.screens.onboarding.FolderAccessLearnMoreFullModeSection
import dev.bikram.filepipe.ui.screens.onboarding.FolderAccessLearnMoreSelectiveModeSection
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.gradientOverlayTopAppBarColors
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.elevatedCardColors
import kotlinx.coroutines.delay

private enum class FaqInlineAction {
    OPEN_FOLDER_ACCESS_IN_SETTINGS,
    OPEN_APP_NOTIFICATION_SETTINGS
}

private enum class FaqItemBodyKind {
    BULLETS,
    STORAGE_FULL_MODE,
    STORAGE_SELECTIVE_MODE
}

private const val SECTION_FIX_COMMON_ISSUES = "fix_common_issues"
private const val SECTION_STORAGE_ACCESS_MODES = "storage_access_modes"

private data class FaqItemDefinition(
    val id: String,
    @param:StringRes val questionRes: Int,
    /**
     * Multiline string: each non-blank line is one bullet. Null when the body is not bullet content
     * (e.g. storage mode sections). Plain strings avoid AAPT2 string-array parse bugs on some AGP versions.
     */
    @param:StringRes val bulletTextRes: Int? = null,
    val inlineActions: List<FaqInlineAction> = emptyList(),
    val bodyKind: FaqItemBodyKind = FaqItemBodyKind.BULLETS
)

private data class FaqSectionDefinition(
    val sectionId: String,
    @param:StringRes val titleRes: Int,
    val showNotSureBanner: Boolean = false,
    @param:StringRes val sectionCalloutRes: Int? = null,
    val items: List<FaqItemDefinition>
)

private data class FaqItemContent(
    val id: String,
    val question: String,
    val bullets: List<String>,
    val inlineActions: List<FaqInlineAction>,
    val bodyKind: FaqItemBodyKind,
    val searchHaystack: String
)

private data class FaqSectionContent(
    val sectionId: String,
    val title: String,
    val showNotSureBanner: Boolean,
    val calloutBody: String?,
    val items: List<FaqItemContent>
)

private fun parseDoubleAsteriskEmphasis(text: String): AnnotatedString {
    val parts = text.split("**")
    return buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
    }
}

private fun faqPlainTextForSearch(text: String): String = text.replace("**", "")

private fun storageSectionMatchesQuery(query: String, sectionTitle: String): Boolean {
    if (sectionTitle.lowercase().contains(query)) return true
    val tokens = listOf(
        "full", "files", "access", "selective", "folder", "download", "storage",
        "permission", "private", "organize", "manage", "grant", "choose", "pick"
    )
    return tokens.any { token -> token.contains(query) || query.contains(token) }
}

private fun computeLazyIndexForSectionHeader(
    focusSectionId: String,
    filteredSections: List<Pair<FaqSectionContent, List<FaqItemContent>>>
): Int {
    var index = 1
    for ((section, matchingItems) in filteredSections) {
        if (section.sectionId == focusSectionId) return index
        index += 1
        if (section.showNotSureBanner) index += 1
        if (section.calloutBody != null) index += 1
        index += matchingItems.size
    }
    return -1
}

private fun buildStorageSearchHaystack(vararg parts: String): String =
    parts.joinToString(" ").lowercase()

private fun faqBulletLinesFromMultilineString(text: String): List<String> =
    text.replace("\r\n", "\n")
        .lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() }
        .toList()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FaqScreen(
    initialFocusSectionId: String,
    onNavigateBack: () -> Unit,
    onOpenFolderAccessInSettings: () -> Unit,
    onOpenSettingsNotifications: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit
) {
    val playTap = rememberPlayTapSound()
    val latestFolderAccess by rememberUpdatedState(onOpenFolderAccessInSettings)
    val latestNotifications by rememberUpdatedState(onOpenSettingsNotifications)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val activity = LocalContext.current as ComponentActivity
    val faqViewModel: FaqViewModel = hiltViewModel(activity)
    val expandedItemIds by faqViewModel.expandedItemIds.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val fullBleedBlurModifier = LocalProgressiveBlurStyle.current?.applyToFullBleedLayer() ?: Modifier

    val faqSections = remember {
        listOf(
            FaqSectionDefinition(
                sectionId = SECTION_FIX_COMMON_ISSUES,
                titleRes = R.string.faq_section_fix_common_issues,
                items = listOf(
                    FaqItemDefinition(
                        id = "rule_not_working",
                        questionRes = R.string.faq_question_rule_not_working,
                        bulletTextRes = R.string.faq_answer_rule_not_working,
                        inlineActions = listOf(FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS)
                    ),
                    FaqItemDefinition(
                        id = "folder_access_denied",
                        questionRes = R.string.faq_question_folder_access_denied,
                        bulletTextRes = R.string.faq_answer_folder_access_denied,
                        inlineActions = listOf(FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS)
                    ),
                    FaqItemDefinition(
                        id = "cant_add_downloads_folder",
                        questionRes = R.string.faq_question_cant_add_downloads_folder,
                        bulletTextRes = R.string.faq_answer_cant_add_downloads_folder,
                        inlineActions = listOf(FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS)
                    ),
                    FaqItemDefinition(
                        id = "files_not_moving_automatically",
                        questionRes = R.string.faq_question_files_not_moving_automatically,
                        bulletTextRes = R.string.faq_answer_files_not_moving_automatically,
                        inlineActions = listOf(
                            FaqInlineAction.OPEN_APP_NOTIFICATION_SETTINGS,
                            FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS
                        )
                    )
                )
            ),
            FaqSectionDefinition(
                sectionId = SECTION_STORAGE_ACCESS_MODES,
                titleRes = R.string.faq_section_storage_access_modes,
                showNotSureBanner = true,
                items = listOf(
                    FaqItemDefinition(
                        id = "storage_all_files",
                        questionRes = R.string.onboarding_permissions_sheet_full_mode,
                        bulletTextRes = null,
                        bodyKind = FaqItemBodyKind.STORAGE_FULL_MODE,
                        inlineActions = listOf(FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS)
                    ),
                    FaqItemDefinition(
                        id = "storage_selective",
                        questionRes = R.string.onboarding_permissions_sheet_select_mode,
                        bulletTextRes = null,
                        bodyKind = FaqItemBodyKind.STORAGE_SELECTIVE_MODE,
                        inlineActions = listOf(FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS)
                    )
                )
            ),
            FaqSectionDefinition(
                sectionId = "managing_rules",
                titleRes = R.string.faq_section_managing_rules,
                items = listOf(
                    FaqItemDefinition(
                        id = "create_rule",
                        questionRes = R.string.faq_question_create_rule,
                        bulletTextRes = R.string.faq_answer_create_rule
                    ),
                    FaqItemDefinition(
                        id = "rule_skipping_files",
                        questionRes = R.string.faq_question_rule_skipping_files,
                        bulletTextRes = R.string.faq_answer_rule_skipping_files
                    ),
                    FaqItemDefinition(
                        id = "what_preview_does",
                        questionRes = R.string.faq_question_what_preview_does,
                        bulletTextRes = R.string.faq_answer_what_preview_does
                    )
                )
            ),
            FaqSectionDefinition(
                sectionId = "automation_scheduling",
                titleRes = R.string.faq_section_automation_scheduling,
                items = listOf(
                    FaqItemDefinition(
                        id = "scheduled_rules",
                        questionRes = R.string.faq_question_scheduled_rules,
                        bulletTextRes = R.string.faq_answer_scheduled_rules
                    ),
                    FaqItemDefinition(
                        id = "schedule_notifications",
                        questionRes = R.string.faq_question_schedule_notifications,
                        bulletTextRes = R.string.faq_answer_schedule_notifications,
                        inlineActions = listOf(
                            FaqInlineAction.OPEN_APP_NOTIFICATION_SETTINGS,
                            FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS
                        )
                    ),
                    FaqItemDefinition(
                        id = "rule_did_not_run",
                        questionRes = R.string.faq_question_rule_did_not_run,
                        bulletTextRes = R.string.faq_answer_rule_did_not_run,
                        inlineActions = listOf(
                            FaqInlineAction.OPEN_APP_NOTIFICATION_SETTINGS,
                            FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS
                        )
                    )
                )
            ),
            FaqSectionDefinition(
                sectionId = "privacy_permissions",
                titleRes = R.string.faq_section_privacy_permissions,
                items = listOf(
                    FaqItemDefinition(
                        id = "data_privacy",
                        questionRes = R.string.faq_question_data_privacy,
                        bulletTextRes = R.string.faq_answer_data_privacy
                    ),
                    FaqItemDefinition(
                        id = "why_storage_access",
                        questionRes = R.string.faq_question_why_storage_access,
                        bulletTextRes = R.string.faq_answer_why_storage_access,
                        inlineActions = listOf(FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS)
                    )
                )
            ),
            FaqSectionDefinition(
                sectionId = "backup_restore",
                titleRes = R.string.faq_section_backup_restore,
                items = listOf(
                    FaqItemDefinition(
                        id = "backup_contents",
                        questionRes = R.string.faq_question_backup_contents,
                        bulletTextRes = R.string.faq_answer_backup_contents
                    ),
                    FaqItemDefinition(
                        id = "permissions_after_restore",
                        questionRes = R.string.faq_question_permissions_after_restore,
                        bulletTextRes = R.string.faq_answer_permissions_after_restore,
                        inlineActions = listOf(FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS)
                    )
                )
            )
        )
    }

    val allExpandableItemIds = remember {
        faqSections.flatMap { section -> section.items.map { item -> item.id } }
    }

    val allItemsExpanded = remember(expandedItemIds, allExpandableItemIds) {
        allExpandableItemIds.isNotEmpty() &&
            allExpandableItemIds.all { itemId -> itemId in expandedItemIds }
    }

    val resolvedSections = faqSections.map { sectionDefinition ->
        FaqSectionContent(
            sectionId = sectionDefinition.sectionId,
            title = stringResource(sectionDefinition.titleRes),
            showNotSureBanner = sectionDefinition.showNotSureBanner,
            calloutBody = sectionDefinition.sectionCalloutRes?.let { stringResource(it) },
            items = sectionDefinition.items.map { itemDefinition ->
                val haystack = when (itemDefinition.bodyKind) {
                    FaqItemBodyKind.STORAGE_FULL_MODE -> buildStorageSearchHaystack(
                        stringResource(R.string.onboarding_permissions_sheet_full_subtitle),
                        stringResource(R.string.onboarding_permissions_sheet_full_use_intro),
                        stringResource(R.string.onboarding_permissions_sheet_full_bullet1),
                        stringResource(R.string.onboarding_permissions_sheet_full_bullet2),
                        stringResource(R.string.onboarding_permissions_sheet_full_bullet3),
                        stringResource(R.string.onboarding_permissions_sheet_full_good_to_know),
                        stringResource(R.string.onboarding_permissions_sheet_full_note1),
                        stringResource(R.string.onboarding_permissions_sheet_full_note2)
                    )
                    FaqItemBodyKind.STORAGE_SELECTIVE_MODE -> buildStorageSearchHaystack(
                        stringResource(R.string.onboarding_permissions_sheet_select_subtitle),
                        stringResource(R.string.onboarding_permissions_sheet_select_use_intro),
                        stringResource(R.string.onboarding_permissions_sheet_select_bullet1),
                        stringResource(R.string.onboarding_permissions_sheet_select_bullet2),
                        stringResource(R.string.onboarding_permissions_sheet_select_limitations),
                        stringResource(R.string.onboarding_permissions_sheet_select_lim1),
                        stringResource(R.string.onboarding_permissions_sheet_select_lim2)
                    )
                    FaqItemBodyKind.BULLETS -> ""
                }
                FaqItemContent(
                    id = itemDefinition.id,
                    question = stringResource(itemDefinition.questionRes),
                    bullets = itemDefinition.bulletTextRes?.let { textResId ->
                        faqBulletLinesFromMultilineString(stringResource(textResId))
                    } ?: emptyList(),
                    inlineActions = itemDefinition.inlineActions,
                    bodyKind = itemDefinition.bodyKind,
                    searchHaystack = haystack
                )
            }
        )
    }

    val normalizedQuery = searchQuery.trim().lowercase()

    val filteredSections = remember(resolvedSections, normalizedQuery) {
        resolvedSections.mapNotNull { sectionContent ->
            if (sectionContent.sectionId == SECTION_STORAGE_ACCESS_MODES) {
                if (normalizedQuery.isNotBlank() &&
                    !storageSectionMatchesQuery(normalizedQuery, sectionContent.title)
                ) {
                    val matchingItems = sectionContent.items.filter { itemContent ->
                        faqItemMatchesQuery(
                            question = itemContent.question,
                            bullets = itemContent.bullets,
                            query = normalizedQuery,
                            searchHaystack = itemContent.searchHaystack
                        )
                    }
                    if (matchingItems.isEmpty()) return@mapNotNull null
                    sectionContent to matchingItems
                } else {
                    sectionContent to sectionContent.items
                }
            } else {
                val matchingItems = sectionContent.items.filter { itemContent ->
                    normalizedQuery.isBlank() || faqItemMatchesQuery(
                        question = itemContent.question,
                        bullets = itemContent.bullets,
                        query = normalizedQuery,
                        searchHaystack = itemContent.searchHaystack
                    )
                }
                if (matchingItems.isEmpty()) null else sectionContent to matchingItems
            }
        }
    }

    LaunchedEffect(initialFocusSectionId, filteredSections) {
        if (initialFocusSectionId != Screen.Faq.FOCUS_STORAGE_ACCESS) return@LaunchedEffect
        if (!expandedItemIds.contains("storage_all_files")) {
            faqViewModel.setItemExpanded("storage_all_files", true)
        }
        if (!expandedItemIds.contains("storage_selective")) {
            faqViewModel.setItemExpanded("storage_selective", true)
        }
        val scrollIndex = computeLazyIndexForSectionHeader(
            focusSectionId = SECTION_STORAGE_ACCESS_MODES,
            filteredSections = filteredSections
        )
        if (scrollIndex < 0) return@LaunchedEffect
        delay(120)
        listState.scrollToItem(scrollIndex)
    }

    val scheme = MaterialTheme.colorScheme
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (LocalUseGradientBackground.current) {
                    Modifier
                        .background(scheme.surface)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to scheme.primaryContainer.copy(alpha = 0.45f),
                                    0.55f to scheme.surface.copy(alpha = 0f)
                                )
                            )
                        )
                } else {
                    Modifier.background(MaterialTheme.colorScheme.background)
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(fullBleedBlurModifier)
        ) {
            if (LocalUseGradientBackground.current) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scheme.surface)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to scheme.primaryContainer.copy(alpha = 0.45f),
                                    0.55f to scheme.surface.copy(alpha = 0f)
                                )
                            )
                        )
                )
            } else {
                Box(Modifier.fillMaxSize().background(scheme.background))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = statusTop + 64.dp,
                    bottom = navBottom + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        playTap()
                                        latestFolderAccess()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(stringResource(R.string.faq_quick_action_folder_access))
                                }
                                Button(
                                    onClick = {
                                        playTap()
                                        latestNotifications()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(stringResource(R.string.faq_quick_action_notifications))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { updatedQuery -> searchQuery = updatedQuery },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            placeholder = { Text(stringResource(R.string.faq_search_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = if (searchQuery.isNotBlank()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.faq_clear_search)
                                        )
                                    }
                                }
                            } else {
                                null
                            }
                        )
                    }
                }

                if (filteredSections.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.faq_no_results_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.faq_no_results_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    filteredSections.forEach { (sectionContent, itemContents) ->
                        item(key = "section_${sectionContent.sectionId}") {
                            Text(
                                text = sectionContent.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (sectionContent.showNotSureBanner) {
                            item(key = "not_sure_${sectionContent.sectionId}") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.onboarding_permissions_sheet_footer_tip),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                        sectionContent.calloutBody?.let { calloutText ->
                            item(key = "callout_${sectionContent.sectionId}") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                ) {
                                    Text(
                                        text = parseDoubleAsteriskEmphasis(calloutText),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        itemsIndexed(
                            items = itemContents,
                            key = { _, itemContent -> itemContent.id }
                        ) { _, itemContent ->
                            val isExpanded = itemContent.id in expandedItemIds
                            val isTopPriority =
                                sectionContent.sectionId == SECTION_FIX_COMMON_ISSUES &&
                                    (itemContent.id == "rule_not_working" ||
                                        itemContent.id == "folder_access_denied")
                            val cardColors = elevatedCardColors()
                            val cardBackground = when {
                                isTopPriority ->
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                else -> cardColors.containerColor
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cardBackground,
                                contentColor = cardColors.contentColor,
                                border = if (isTopPriority) {
                                    BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    )
                                } else {
                                    null
                                },
                                tonalElevation = if (isTopPriority) 4.dp else 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        faqViewModel.setItemExpanded(itemContent.id, !isExpanded)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = parseDoubleAsteriskEmphasis(itemContent.question),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = if (isExpanded) {
                                                Icons.Default.ExpandLess
                                            } else {
                                                Icons.Default.ExpandMore
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp)
                                                .animateContentSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            when (itemContent.bodyKind) {
                                                FaqItemBodyKind.STORAGE_FULL_MODE -> {
                                                    FolderAccessLearnMoreFullModeSection(
                                                        modifier = Modifier.padding(top = 4.dp),
                                                        showModeTitleInBody = false
                                                    )
                                                }
                                                FaqItemBodyKind.STORAGE_SELECTIVE_MODE -> {
                                                    FolderAccessLearnMoreSelectiveModeSection(
                                                        modifier = Modifier.padding(top = 4.dp),
                                                        showModeTitleInBody = false
                                                    )
                                                }
                                                FaqItemBodyKind.BULLETS -> {
                                                    itemContent.bullets.forEach { bulletText ->
                                                        val trimmed = bulletText.trim()
                                                        if (trimmed.isNotEmpty()) {
                                                            Text(
                                                                text = buildAnnotatedString {
                                                                    append("• ")
                                                                    append(parseDoubleAsteriskEmphasis(bulletText))
                                                                },
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            if (itemContent.inlineActions.isNotEmpty()) {
                                                val actionOrder = listOf(
                                                    FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS,
                                                    FaqInlineAction.OPEN_APP_NOTIFICATION_SETTINGS
                                                ).filter { ordered -> ordered in itemContent.inlineActions }
                                                FlowRow(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    actionOrder.forEach { action ->
                                                        when (action) {
                                                            FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS -> {
                                                                FilledTonalButton(
                                                                    onClick = {
                                                                        playTap()
                                                                        onOpenFolderAccessInSettings()
                                                                    }
                                                                ) {
                                                                    Text(
                                                                        stringResource(
                                                                            R.string.faq_action_switch_full_access
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                            FaqInlineAction.OPEN_APP_NOTIFICATION_SETTINGS -> {
                                                                FilledTonalButton(
                                                                    onClick = {
                                                                        playTap()
                                                                        onOpenAppNotificationSettings()
                                                                    }
                                                                ) {
                                                                    Text(
                                                                        stringResource(
                                                                            R.string.faq_action_notification_settings
                                                                        )
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
                    }
                }
            }
        }

        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = { Text(stringResource(R.string.faq_title)) },
            navigationIcon = {
                IconButton(onClick = {
                    playTap()
                    onNavigateBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back)
                    )
                }
            },
            actions = {
                FilledTonalIconButton(
                    onClick = {
                        playTap()
                        if (allItemsExpanded) {
                            faqViewModel.collapseAll()
                        } else {
                            faqViewModel.expandAll(allExpandableItemIds)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (allItemsExpanded) {
                            Icons.Default.UnfoldLess
                        } else {
                            Icons.Default.UnfoldMore
                        },
                        contentDescription = if (allItemsExpanded) {
                            stringResource(R.string.faq_collapse_all_cd)
                        } else {
                            stringResource(R.string.faq_expand_all_cd)
                        }
                    )
                }
            },
            colors = gradientOverlayTopAppBarColors()
        )
    }
}

private fun faqItemMatchesQuery(
    question: String,
    bullets: List<String>,
    query: String,
    searchHaystack: String
): Boolean {
    if (query.isBlank()) return true
    if (faqPlainTextForSearch(question).lowercase().contains(query)) return true
    if (searchHaystack.contains(query)) return true
    return bullets.any { bullet ->
        faqPlainTextForSearch(bullet).lowercase().contains(query)
    }
}
