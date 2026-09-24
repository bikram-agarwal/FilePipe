package dev.bikram.filepipe.update

private val recognizedPrereleasePattern =
    Regex(
        pattern = """^(.+)-(preview|alpha|beta|rc)(?:[.-]?\d+)?$""",
        option = RegexOption.IGNORE_CASE,
    )

private fun normalizeLeadingVersionPrefix(version: String): String {
    val trimmedVersion = version.trim()
    return if (trimmedVersion.length > 1 &&
        trimmedVersion[0].equals('v', ignoreCase = true) &&
        trimmedVersion[1].isDigit()
    ) {
        trimmedVersion.substring(1)
    } else {
        trimmedVersion
    }
}

private fun compareMatchingPrereleaseAndStableVersions(
    left: String,
    right: String,
): Int? {
    val normalizedLeft = normalizeLeadingVersionPrefix(left).lowercase()
    val normalizedRight = normalizeLeadingVersionPrefix(right).lowercase()
    val leftPrerelease = recognizedPrereleasePattern.matchEntire(normalizedLeft)
    val rightPrerelease = recognizedPrereleasePattern.matchEntire(normalizedRight)

    if (leftPrerelease?.groupValues?.get(1) == normalizedRight) return -1
    if (rightPrerelease?.groupValues?.get(1) == normalizedLeft) return 1
    return null
}

/**
 * Compares two dotted version strings (e.g. "1.5.0" vs "1.10.1").
 * Returns negative if [left] is older than [right], zero if equal, positive if [left] is newer.
 * Non-numeric segments sort after numeric ones; missing segments treated as 0.
 */
internal fun compareVersionNames(
    left: String,
    right: String,
): Int {
    compareMatchingPrereleaseAndStableVersions(left, right)?.let { return it }

    val leftParts = normalizeLeadingVersionPrefix(left).lowercase().split('.', '-', limit = 10)
    val rightParts = normalizeLeadingVersionPrefix(right).lowercase().split('.', '-', limit = 10)
    val maxLen = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until maxLen) {
        val leftToken = leftParts.getOrNull(index).orEmpty()
        val rightToken = rightParts.getOrNull(index).orEmpty()
        val leftNum = leftToken.toIntOrNull()
        val rightNum = rightToken.toIntOrNull()
        val cmp =
            when {
                leftNum != null && rightNum != null -> leftNum.compareTo(rightNum)
                leftNum != null -> -1
                rightNum != null -> 1
                else -> leftToken.compareTo(rightToken)
            }
        if (cmp != 0) return cmp
    }
    return 0
}

/** True if [remote] is strictly newer than [installed] per [compareVersionNames]. */
internal fun isRemoteVersionNewer(
    remote: String,
    installed: String,
): Boolean = compareVersionNames(remote, installed) > 0
