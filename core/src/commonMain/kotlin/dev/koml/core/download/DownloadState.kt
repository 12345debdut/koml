package dev.koml.core.download

import dev.koml.core.error.KomlException
import dev.koml.core.model.ModelHandle

sealed class DownloadState {
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : DownloadState()

    data class Completed(val handle: ModelHandle) : DownloadState()

    data class Failed(val error: KomlException) : DownloadState()

    data object Paused : DownloadState()
}
