package dev.bikram.filepipe.ui.screens.help

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Activity-scoped expansion state so FAQ cards stay expanded or collapsed when the user
 * leaves Help and returns in the same session.
 */
@HiltViewModel
class FaqViewModel @Inject constructor() : ViewModel() {

    private val expandedItemIdsMutable = MutableStateFlow<Set<String>>(emptySet())

    val expandedItemIds: StateFlow<Set<String>> = expandedItemIdsMutable.asStateFlow()

    fun setItemExpanded(itemId: String, expanded: Boolean) {
        expandedItemIdsMutable.update { current ->
            if (expanded) current + itemId else current - itemId
        }
    }

    fun expandAll(itemIds: Collection<String>) {
        expandedItemIdsMutable.value = itemIds.toSet()
    }

    fun collapseAll() {
        expandedItemIdsMutable.value = emptySet()
    }
}
