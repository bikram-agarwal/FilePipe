package dev.bikram.filepipe.ui.screens.help

enum class FaqInlineAction {
    OPEN_FOLDER_ACCESS_IN_SETTINGS,
    OPEN_APP_NOTIFICATION_SETTINGS,
}

enum class FaqItemBodyKind {
    BULLETS,
    STORAGE_FULL_MODE,
    STORAGE_SELECTIVE_MODE,
}

data class FaqSectionContent(
    val sectionId: String,
    val title: String,
    val showNotSureBanner: Boolean,
    val calloutBody: String?,
    val items: List<FaqItemContent>,
)

data class FaqItemContent(
    val id: String,
    val question: String,
    val bullets: List<String>,
    val inlineActions: List<FaqInlineAction>,
    val bodyKind: FaqItemBodyKind,
    val searchHaystack: String,
)

private data class SectionOptions(
    val id: String,
    val showNotSureBanner: Boolean = false,
    val calloutBody: String? = null,
)

private data class ItemOptions(
    val id: String,
    val actions: List<FaqInlineAction> = emptyList(),
    val bodyKind: FaqItemBodyKind = FaqItemBodyKind.BULLETS,
)

private val folderAccessAction = listOf(FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS)

private val notificationAndFolderAccessActions =
    listOf(
        FaqInlineAction.OPEN_APP_NOTIFICATION_SETTINGS,
        FaqInlineAction.OPEN_FOLDER_ACCESS_IN_SETTINGS,
    )

private val sectionOptionsByTitle =
    mapOf(
        "Fix common issues" to SectionOptions("fix_common_issues"),
        "Getting started" to SectionOptions("getting_started"),
        "Storage access modes" to SectionOptions("storage_access_modes", showNotSureBanner = true),
        "Managing rules" to SectionOptions("managing_rules"),
        "History & undo" to SectionOptions("history_undo"),
        "Automation & scheduling" to SectionOptions("automation_scheduling"),
        "Privacy & permissions" to SectionOptions("privacy_permissions"),
        "Backup & restore" to SectionOptions("backup_restore"),
        "Customization" to SectionOptions("customization"),
    )

private val itemOptionsByTitle =
    mapOf(
        "Fix a rule that isn't working" to
            ItemOptions("rule_not_working", actions = folderAccessAction),
        "Fix \"Folders need attention\" errors" to
            ItemOptions("folder_access_denied", actions = folderAccessAction),
        "Can't add the Download folder?" to
            ItemOptions("cant_add_downloads_folder", actions = folderAccessAction),
        "Files not moving?" to
            ItemOptions("files_not_moving_automatically", actions = notificationAndFolderAccessActions),
        "All files access" to
            ItemOptions(
                "storage_all_files",
                actions = folderAccessAction,
                bodyKind = FaqItemBodyKind.STORAGE_FULL_MODE,
            ),
        "Selective access" to
            ItemOptions(
                "storage_selective",
                actions = folderAccessAction,
                bodyKind = FaqItemBodyKind.STORAGE_SELECTIVE_MODE,
            ),
        "How do I create a rule?" to ItemOptions("create_rule"),
        "What are templates?" to ItemOptions("templates"),
        "How do I run a rule manually?" to ItemOptions("run_manually"),
        "Why is my rule skipping files?" to ItemOptions("rule_skipping_files"),
        "What does preview do?" to ItemOptions("what_preview_does"),
        "Can rules scan subfolders?" to ItemOptions("subfolders"),
        "What is in History?" to ItemOptions("history_contents"),
        "How do I undo a rule run?" to ItemOptions("undo_run"),
        "How do scheduled rules work?" to ItemOptions("scheduled_rules"),
        "Not getting scheduled run notifications?" to
            ItemOptions("schedule_notifications", actions = notificationAndFolderAccessActions),
        "Why did my rule not run?" to
            ItemOptions("rule_did_not_run", actions = notificationAndFolderAccessActions),
        "Is my data private?" to ItemOptions("data_privacy"),
        "Why does FilePipe need storage access?" to
            ItemOptions("why_storage_access", actions = folderAccessAction),
        "What data does FilePipe store on my device?" to ItemOptions("what_data_stored"),
        "What gets backed up?" to ItemOptions("backup_contents"),
        "Can I schedule automatic backups?" to ItemOptions("scheduled_backup"),
        "What do I fix after restore?" to
            ItemOptions("permissions_after_restore", actions = folderAccessAction),
        "Theme and appearance" to ItemOptions("theme_appearance"),
        "Colors" to ItemOptions("colors"),
        "Visual effects" to ItemOptions("visual_effects"),
    )

fun parseHelpContent(markdown: String): List<FaqSectionContent> {
    val sections = mutableListOf<FaqSectionContent>()
    var sectionHeading: ParsedSectionHeading? = null
    val items = mutableListOf<FaqItemContent>()
    var itemHeading: ParsedItemHeading? = null
    val bodyLines = mutableListOf<String>()

    fun flushItem() {
        val heading = itemHeading ?: return
        val body = bodyLines.joinToString("\n").trim()
        items.add(
            FaqItemContent(
                id = heading.id,
                question = heading.title,
                bullets = body.toBulletLines(),
                inlineActions = heading.actions,
                bodyKind = heading.bodyKind,
                searchHaystack = body,
            ),
        )
        itemHeading = null
        bodyLines.clear()
    }

    fun flushSection() {
        flushItem()
        val heading = sectionHeading ?: return
        if (items.isNotEmpty()) {
            sections.add(
                FaqSectionContent(
                    sectionId = heading.id,
                    title = heading.title,
                    showNotSureBanner = heading.showNotSureBanner,
                    calloutBody = heading.calloutBody,
                    items = items.toList(),
                ),
            )
        }
        sectionHeading = null
        items.clear()
    }

    markdown
        .lines()
        .forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.startsWith("# ") -> {}

                line.startsWith("## ") -> {
                    flushSection()
                    sectionHeading = parseSectionHeading(line.removePrefix("## "))
                }

                line.startsWith("### ") -> {
                    flushItem()
                    itemHeading = parseItemHeading(line.removePrefix("### "))
                }

                line == "---" -> {}

                itemHeading != null -> {
                    bodyLines.add(line)
                }
            }
        }
    flushSection()
    return sections
}

private data class ParsedSectionHeading(
    val title: String,
    val id: String,
    val showNotSureBanner: Boolean,
    val calloutBody: String?,
)

private data class ParsedItemHeading(
    val title: String,
    val id: String,
    val actions: List<FaqInlineAction>,
    val bodyKind: FaqItemBodyKind,
)

private fun parseSectionHeading(rawHeading: String): ParsedSectionHeading {
    val title = rawHeading.trim()
    val options = sectionOptionsByTitle[title]
    return ParsedSectionHeading(
        title = title,
        id = options?.id ?: title.toHelpId(),
        showNotSureBanner = options?.showNotSureBanner ?: false,
        calloutBody = options?.calloutBody,
    )
}

private fun parseItemHeading(rawHeading: String): ParsedItemHeading {
    val title = rawHeading.trim()
    val options = itemOptionsByTitle[title]
    return ParsedItemHeading(
        title = title,
        id = options?.id ?: title.toHelpId(),
        actions = options?.actions.orEmpty(),
        bodyKind = options?.bodyKind ?: FaqItemBodyKind.BULLETS,
    )
}

private fun String.toBulletLines(): List<String> =
    lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() && !line.startsWith("<!--") }
        .map { line -> line.removePrefix("- ").trim() }
        .filter { line -> line.isNotEmpty() }
        .toList()

private fun String.toHelpId(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
