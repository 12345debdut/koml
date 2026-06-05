package dev.koml.core.download

import dev.koml.core.error.KomlException
import dev.koml.core.model.ModelHandle

/**
 * Events emitted by [ModelDownloader.download]. Exactly one terminal state
 * ([Completed], [Failed], or [Paused]) ends the flow.
 */
sealed class DownloadState {

    /**
     * Progress update — throttled to ~4 Hz so UI consumers don't recompose
     * on every chunk. [bytesPerSecond] is averaged over the last emit window
     * (roughly 250 ms), so the first event often reports `0`.
     */
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : DownloadState()

    /**
     * Download succeeded: the file has been atomically renamed to its
     * final path and SHA-256 verified. [handle] is loadable via
     * [dev.koml.core.LlmCoordinator.loadModel].
     */
    data class Completed(val handle: ModelHandle) : DownloadState()

    /**
     * Download failed at any stage. The specific [KomlException] subtype
     * tells you what went wrong:
     * - [KomlException.LicenseNotAcceptedException] — license gate.
     * - [KomlException.DownloadException] — SHA mismatch, size mismatch, network.
     * - [KomlException.StorageException] — filesystem error.
     */
    data class Failed(val error: KomlException) : DownloadState()

    /**
     * Reserved for future pause/resume UX. v0.0.3 never emits this; resume
     * happens implicitly on the next call to [ModelDownloader.download].
     */
    data object Paused : DownloadState()
}
