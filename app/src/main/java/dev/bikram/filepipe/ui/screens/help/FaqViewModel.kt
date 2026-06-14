package dev.bikram.filepipe.ui.screens.help

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Help content and app-session UI state so the page can reopen where the user left it.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class FaqViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val uiStateStore: FaqUiStateStore,
    ) : ViewModel() {
        val sections: List<FaqSectionContent> =
            context.assets.open("HELP.md").bufferedReader().use { reader ->
                parseHelpContent(reader.readText())
            }

        private val keywordMap: Map<String, Set<String>> =
            context.assets.open("HELP_KEYWORDS.txt").bufferedReader().use { reader ->
                buildMap {
                    reader
                        .lineSequence()
                        .map { line -> line.trim() }
                        .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
                        .forEach { line ->
                            val arrow = line.indexOf("->")
                            if (arrow < 0) return@forEach
                            val phrase = line.substring(0, arrow).trim().lowercase()
                            val itemIds =
                                line
                                    .substring(arrow + 2)
                                    .split("|")
                                    .map { itemId -> itemId.trim() }
                                    .filter { itemId -> itemId.isNotEmpty() }
                                    .toSet()
                            if (phrase.isNotEmpty() && itemIds.isNotEmpty()) {
                                put(phrase, itemIds)
                            }
                        }
                }
            }

        private val searchQueryMutable = MutableStateFlow("")

        val expandedItemIds: StateFlow<Set<String>> = uiStateStore.expandedItemIds
        val scrollPosition: StateFlow<FaqScrollPosition> = uiStateStore.scrollPosition
        val searchQuery: StateFlow<String> = searchQueryMutable

        val filteredSections: StateFlow<List<FaqSectionContent>> =
            searchQueryMutable
                .debounce(300L)
                .map { query ->
                    val normalizedQuery = query.trim().lowercase()
                    if (normalizedQuery.isBlank()) {
                        sections
                    } else {
                        val keywordHits =
                            keywordMap.entries
                                .filter { (phrase, _) -> normalizedQuery.contains(phrase) }
                                .flatMapTo(mutableSetOf()) { (_, itemIds) -> itemIds }
                        val tokens =
                            normalizedQuery
                                .split(Regex("\\s+"))
                                .filter { token -> token.length >= 3 && token !in searchStopwords }
                        sections.mapNotNull { section ->
                            val matchingItems =
                                section.items.filter { item ->
                                    item.id in keywordHits || item.matchesQuery(normalizedQuery, tokens)
                                }
                            if (matchingItems.isEmpty()) {
                                null
                            } else {
                                section.copy(items = matchingItems)
                            }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sections)

        fun setItemExpanded(
            itemId: String,
            expanded: Boolean,
        ) {
            uiStateStore.setItemExpanded(itemId, expanded)
        }

        fun expandAll(itemIds: Collection<String>) {
            uiStateStore.expandAll(itemIds)
        }

        fun collapseAll() {
            uiStateStore.collapseAll()
        }

        fun setScrollPosition(position: FaqScrollPosition) {
            uiStateStore.setScrollPosition(position)
        }

        fun setSearchQuery(query: String) {
            searchQueryMutable.value = query
        }

        private fun FaqItemContent.matchesQuery(
            query: String,
            tokens: List<String>,
        ): Boolean {
            val content =
                buildString {
                    append(question)
                    append(' ')
                    bullets.forEach { bullet ->
                        append(bullet)
                        append(' ')
                    }
                    append(searchHaystack)
                }.replace("**", "").lowercase()
            if (content.contains(query)) return true
            if (tokens.isEmpty()) return false
            val words = content.split(Regex("[\\s\\n\\r,.:;!?()\\[\\]\"]+"))
            return when {
                tokens.size == 1 -> words.any { word -> word.startsWith(tokens[0]) }
                else -> tokens.all { token -> words.any { word -> word.startsWith(token) } }
            }
        }

        companion object {
            private val searchStopwords =
                setOf(
                    "not",
                    "the",
                    "and",
                    "for",
                    "are",
                    "but",
                    "can",
                    "how",
                    "why",
                    "its",
                    "you",
                    "was",
                    "has",
                    "did",
                    "this",
                    "that",
                    "with",
                    "have",
                    "will",
                    "your",
                    "any",
                    "all",
                    "also",
                    "from",
                    "into",
                    "when",
                    "then",
                    "than",
                    "them",
                    "they",
                    "just",
                    "been",
                )
        }
    }
