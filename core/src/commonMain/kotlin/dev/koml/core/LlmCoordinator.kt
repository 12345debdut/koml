package dev.koml.core

import dev.koml.core.config.RuntimeConfig
import dev.koml.core.download.ModelDownloader
import dev.koml.core.model.ModelHandle
import dev.koml.core.registry.ModelRegistry

interface LlmCoordinator {
    val registry: ModelRegistry
    val downloader: ModelDownloader

    suspend fun loadModel(
        handle: ModelHandle,
        runtime: RuntimeConfig = RuntimeConfig(),
    ): LlmSession

    suspend fun activeSessions(): List<LlmSession>
}
