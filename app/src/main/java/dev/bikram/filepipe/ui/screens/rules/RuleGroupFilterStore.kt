package dev.bikram.filepipe.ui.screens.rules

import dev.bikram.filepipe.domain.model.Rule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Rules list's group filter, as the user last picked it. App-wide rather than inside
 * [RulesViewModel] so the rule editor can read it: a rule created while a group is filtered joins that
 * group (RuleDetailViewModel), instead of being saved ungrouped and vanishing from the list it was
 * created from. RulesViewModel resets it to All when cleared, so it still lasts only as long as the list.
 *
 * May name a group that no longer exists; read it through [resolvedFor].
 */
@Singleton
class RuleGroupFilterStore
    @Inject
    constructor() {
        private val filterMutable = MutableStateFlow<RuleGroupFilter>(RuleGroupFilter.All)

        val filter: StateFlow<RuleGroupFilter> = filterMutable.asStateFlow()

        fun setFilter(filter: RuleGroupFilter) {
            filterMutable.value = filter
        }
    }

/**
 * The spelling [groupName] already has among [rules], matching ignoring case, or [groupName] itself
 * when it names no existing group. Groups are told apart by exact name, so without this "work" and
 * "Work" would become two groups that look the same.
 */
internal fun existingRuleGroupSpelling(
    groupName: String,
    rules: List<Rule>,
): String {
    // An exact match wins, should two spellings already exist side by side.
    if (rules.any { rule -> rule.groupName == groupName }) return groupName
    return rules.firstNotNullOfOrNull { rule -> rule.groupName?.takeIf { it.equals(groupName, ignoreCase = true) } }
        ?: groupName
}

/**
 * This filter as the Rules list can apply it to [rules] - the one its chip row shows selected. A group
 * no rule is in any more (its last rule moved out, deleted or trashed) falls back to All, and so does
 * Ungrouped once grouping no longer splits the list (no groups, or no ungrouped rules).
 */
internal fun RuleGroupFilter.resolvedFor(rules: List<Rule>): RuleGroupFilter =
    when (this) {
        RuleGroupFilter.All -> {
            this
        }

        is RuleGroupFilter.Group -> {
            if (rules.any { rule -> rule.groupName == name }) this else RuleGroupFilter.All
        }

        RuleGroupFilter.Ungrouped -> {
            val hasGroups = rules.any { rule -> rule.groupName != null }
            val hasUngrouped = rules.any { rule -> rule.groupName == null }
            if (hasGroups && hasUngrouped) this else RuleGroupFilter.All
        }
    }
