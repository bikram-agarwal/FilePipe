package dev.bikram.filepipe.domain.model

/**
 * Result of checking whether a persisted SAF tree URI is usable right now.
 *
 * [Unavailable] means the grant may still be valid but the tree is missing, not readable, or similar.
 * [PermissionDenied] means access was denied (for example [SecurityException] from the provider).
 */
enum class FolderAccessResult {
    Accessible,
    Unavailable,
    PermissionDenied,
}
