package dev.koml.core.download

import dev.koml.core.model.ModelHandle
import dev.koml.core.model.ModelInfo
import kotlinx.coroutines.flow.Flow

interface ModelDownloader {
    fun download(model: ModelInfo): Flow<DownloadState>

    suspend fun isDownloaded(id: String): Boolean

    suspend fun delete(id: String): Boolean

    suspend fun localModels(): List<ModelHandle>
}
