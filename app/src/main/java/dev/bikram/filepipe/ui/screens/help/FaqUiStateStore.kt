package dev.bikram.filepipe.ui.screens.help

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class FaqScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)

@Singleton
class FaqUiStateStore
    @Inject
    constructor() {
        private val expandedItemIdsMutable = MutableStateFlow<Set<String>>(emptySet())
        private val scrollPositionMutable = MutableStateFlow(FaqScrollPosition())

        val expandedItemIds: StateFlow<Set<String>> = expandedItemIdsMutable.asStateFlow()
        val scrollPosition: StateFlow<FaqScrollPosition> = scrollPositionMutable.asStateFlow()

        fun setItemExpanded(
            itemId: String,
            expanded: Boolean,
        ) {
            expandedItemIdsMutable.value =
                if (expanded) {
                    expandedItemIdsMutable.value + itemId
                } else {
                    expandedItemIdsMutable.value - itemId
                }
        }

        fun expandAll(itemIds: Collection<String>) {
            expandedItemIdsMutable.value = itemIds.toSet()
        }

        fun collapseAll() {
            expandedItemIdsMutable.value = emptySet()
        }

        fun setScrollPosition(position: FaqScrollPosition) {
            scrollPositionMutable.value = position
        }
    }
