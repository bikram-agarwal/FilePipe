package dev.bikram.filepipe.ui.screens.settings

/**
 * Markdown horizontal rule: a line of three or more `-`, `*`, or `_` with optional spaces between.
 * Used to strip `---` dividers that sit between `##` sections in the raw file; they are redundant once paginated.
 */
private val markdownHorizontalRuleLine = Regex("""^\s*([-*_])(?:\s*\1){2,}\s*$""")

private fun trimTrailingHorizontalRulesAndBlankLines(text: String): String {
    val lines = text.lines().toMutableList()
    while (lines.isNotEmpty()) {
        val lastLine = lines.last()
        if (lastLine.isBlank()) {
            lines.removeAt(lines.lastIndex)
        } else if (markdownHorizontalRuleLine.matches(lastLine)) {
            lines.removeAt(lines.lastIndex)
        } else {
            break
        }
    }
    return lines.joinToString("\n")
}

/**
 * Splits [CHANGELOG.md](https://keepachangelog.com)-style text into one page per top-level `##` section.
 * Preamble before the first `##` is merged into the first page so nothing is dropped.
 * Trailing `---` / `***` / `___` lines between sections are removed from each page so they are not shown in the pager.
 */
fun splitChangelogIntoPages(markdown: String): List<String> {
    val trimmed = markdown.trimEnd()
    if (trimmed.isEmpty()) return listOf("")
    val lines = trimmed.lines()
    val chunks = mutableListOf<MutableList<String>>()
    var current = mutableListOf<String>()
    for (line in lines) {
        val isH2Heading = line.startsWith("## ") && !line.startsWith("###")
        if (isH2Heading && current.isNotEmpty()) {
            chunks.add(current)
            current = mutableListOf()
        }
        current.add(line)
    }
    if (current.isNotEmpty()) chunks.add(current)
    return chunks.map { chunk ->
        trimTrailingHorizontalRulesAndBlankLines(chunk.joinToString("\n"))
    }
}
