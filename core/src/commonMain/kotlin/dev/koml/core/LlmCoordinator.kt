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

    /**
     * Records that the user has accepted the license terms for [modelId].
     *
     * Returns `true` if the model exists in the registry and the acceptance
     * was persisted; `false` otherwise (e.g. unknown model id, or a
     * coordinator implementation that does not track license state).
     *
     * The default implementation is a no-op, preserving binary compatibility
     * for coordinators built against earlier Koml versions.
     */
    suspend fun acceptLicense(modelId: String): Boolean = false
}
