package dev.koml.engine.internal

import dev.koml.core.download.DownloadState
import dev.koml.core.download.ModelDownloader
import dev.koml.core.error.KomlException
import dev.koml.core.model.ModelHandle
import dev.koml.core.model.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class StubDownloader : ModelDownloader {
    override fun download(model: ModelInfo): Flow<DownloadState> = flowOf(
        DownloadState.Failed(KomlException.DownloadException("Downloader is implemented in Phase 2")),
    )

    override suspend fun isDownloaded(id: String): Boolean = false
    override suspend fun delete(id: String): Boolean = false
    override suspend fun localModels(): List<ModelHandle> = emptyList()
}
