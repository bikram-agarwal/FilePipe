package dev.bikram.filepipe.data.preferences

/**
 * User-chosen strategy for folder access. [DEFERRED] behaves like [SAF_ONLY] for UI until set in Settings.
 */
enum class FolderAccessMode {
    /** Storage Access Framework only; least privilege. */
    SAF_ONLY,

    /** Prefer filesystem paths when [android.os.Environment.isExternalStorageManager] is true. */
    ALL_FILES_PREFERRED,

    /** Onboarding "decide later"; same UX as [SAF_ONLY] until user picks a concrete mode. */
    DEFERRED
}

fun FolderAccessMode.usesAllFilesPaths(): Boolean = this == FolderAccessMode.ALL_FILES_PREFERRED

fun FolderAccessMode.treatAsSafUi(): Boolean =
    this == FolderAccessMode.SAF_ONLY || this == FolderAccessMode.DEFERRED
