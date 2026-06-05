package dev.koml.core.model

/**
 * A model that's on disk and ready to load. Returned by
 * [dev.koml.core.download.DownloadState.Completed] and by
 * [dev.koml.core.download.ModelDownloader.localModels].
 *
 * @property info the model's static metadata.
 * @property localPath absolute filesystem path to the verified GGUF.
 */
data class ModelHandle(
    val info: ModelInfo,
    val localPath: String,
)
