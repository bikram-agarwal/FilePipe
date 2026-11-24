package dev.bikram.filepipe.ui.screens.rules

import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.PreviewFileResult
import dev.bikram.filepipe.domain.model.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleGroupFilterTest {
    @Test
    fun groupWithRulesStaysSelected() {
        val rules = listOf(rule(1, "Work"), rule(2, null))
        assertEquals(RuleGroupFilter.Group("Work"), RuleGroupFilter.Group("Work").resolvedFor(rules))
    }

    @Test
    fun groupNoRuleIsInFallsBackToAll() {
        // E.g. the group's last rule was moved out, deleted or trashed while it was the filter.
        val rules = listOf(rule(1, "Photos"), rule(2, null))
        assertEquals(RuleGroupFilter.All, RuleGroupFilter.Group("Work").resolvedFor(rules))
    }

    @Test
    fun ungroupedNeedsBothGroupedAndUngroupedRules() {
        assertEquals(
            RuleGroupFilter.Ungrouped,
            RuleGroupFilter.Ungrouped.resolvedFor(listOf(rule(1, "Work"), rule(2, null))),
        )
        assertEquals(RuleGroupFilter.All, RuleGroupFilter.Ungrouped.resolvedFor(listOf(rule(1, null))))
        assertEquals(RuleGroupFilter.All, RuleGroupFilter.Ungrouped.resolvedFor(listOf(rule(1, "Work"))))
    }

    @Test
    fun allIsAlwaysAll() {
        assertEquals(RuleGroupFilter.All, RuleGroupFilter.All.resolvedFor(emptyList()))
    }

    @Test
    fun runTallyMovesAFileBetweenOutcomesWithoutDoubleCounting() {
        val started = PreviewRuleRunTally().adjusted(null, -1).adjusted(PreviewFileRunStatus.IN_PROGRESS, +1)
        assertEquals(0, started.settled)

        val finished = started.adjusted(PreviewFileRunStatus.IN_PROGRESS, -1).adjusted(PreviewFileRunStatus.DONE, +1)
        assertEquals(PreviewRuleRunTally(done = 1), finished)

        // A status replaced by another final one (not expected, but must not inflate the counts).
        val replaced = finished.adjusted(PreviewFileRunStatus.DONE, -1).adjusted(PreviewFileRunStatus.FAILED, +1)
        assertEquals(PreviewRuleRunTally(failed = 1), replaced)
        assertEquals(1, replaced.settled)
    }

    @Test
    fun statusStoreReturnsWhatItReplaced() {
        val statuses = PreviewRunFileStatuses()
        val file = PreviewRunFile(ruleId = 7, sourcePath = "content://x/a.jpg")

        assertNull(statuses.put(file, PreviewFileRunStatus.IN_PROGRESS))
        assertEquals(PreviewFileRunStatus.IN_PROGRESS, statuses.put(file, PreviewFileRunStatus.DONE))
        assertEquals(PreviewFileRunStatus.DONE, statuses[7, "content://x/a.jpg"])

        statuses.remove(file)
        assertNull(statuses[7, "content://x/a.jpg"])
    }

    @Test
    fun newGroupNameReusesAnExistingGroupsSpellingIgnoringCase() {
        val rules = listOf(rule(1, "Work"), rule(2, null))
        assertEquals("Work", existingRuleGroupSpelling("work", rules))
        assertEquals("Music", existingRuleGroupSpelling("Music", rules))
    }

    @Test
    fun exactGroupSpellingWinsWhenTwoAlreadyExist() {
        val rules = listOf(rule(1, "Work"), rule(2, "work"))
        assertEquals("work", existingRuleGroupSpelling("work", rules))
    }

    @Test
    fun dragInFilteredListReordersOnlyTheVisibleRulesSlots() {
        // My order A, B, C, D, E; the filter shows B and D, and the user drags D above B.
        val all = (1L..5L).map { id -> rule(id, if (id == 2L || id == 4L) "Work" else null) }
        val merged = mergeVisibleRuleOrder(all, listOf(all[3], all[1]))
        assertEquals(listOf(1L, 4L, 3L, 2L, 5L), merged.map { it.id })
    }

    @Test
    fun dragInUnfilteredListIsTheWholeOrder() {
        val all = (1L..3L).map { id -> rule(id, null) }
        val reordered = listOf(all[2], all[0], all[1])
        assertEquals(reordered, mergeVisibleRuleOrder(all, reordered))
    }

    @Test
    fun runnableGroupsAreCheckedAgainstEveryEnabledRuleNotTheVisibleList() {
        // A launcher shortcut previews rule 9 while the list is filtered to another group: it must
        // still be runnable, since enabledRuleIds covers all rules.
        val preview =
            PreviewState(
                isLoading = false,
                ruleGroups =
                    listOf(
                        previewGroup(ruleId = 9, wouldSkip = false),
                        previewGroup(ruleId = 10, wouldSkip = true),
                        previewGroup(ruleId = 11, wouldSkip = false),
                    ),
            )
        val runnable = preview.runnableRuleGroups(enabledRuleIds = setOf(9L, 10L))
        // 10 has nothing to do; 11 is disabled or gone.
        assertEquals(listOf(9L), runnable.map { it.ruleId })
    }

    private fun previewGroup(
        ruleId: Long,
        wouldSkip: Boolean,
    ) = PreviewRuleGroup(
        ruleId = ruleId,
        ruleName = "Rule $ruleId",
        operationMode = OperationMode.MOVE,
        results =
            listOf(
                PreviewFileResult(
                    fileName = "a.jpg",
                    sourcePath = "content://x/$ruleId/a.jpg",
                    simulatedDestPath = "",
                    wouldSkip = wouldSkip,
                    wouldOverwrite = false,
                    renamedTo = null,
                    sizeBytes = 1L,
                ),
            ),
    )

    private fun rule(
        id: Long,
        groupName: String?,
    ) = Rule(
        id = id,
        name = "Rule $id",
        sourceFolderPaths = emptyList(),
        destinationFolderPath = "",
        fileExtensions = emptyList(),
        groupName = groupName,
    )
}
