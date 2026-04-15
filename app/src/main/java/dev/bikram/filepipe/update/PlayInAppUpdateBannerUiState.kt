package dev.bikram.filepipe.update

/**
 * Play in-app update progress shown in the root navigation scaffold (Play flavor only).
 * GitHub builds always keep [Hidden].
 */
sealed interface PlayInAppUpdateBannerUiState {
    data object Hidden : PlayInAppUpdateBannerUiState

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long,
        val indeterminateProgress: Boolean
    ) : PlayInAppUpdateBannerUiState

    data object ReadyToInstall : PlayInAppUpdateBannerUiState
}
