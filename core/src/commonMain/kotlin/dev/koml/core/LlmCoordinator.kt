package dev.koml.core

import dev.koml.core.config.RuntimeConfig
import dev.koml.core.download.ModelDownloader
import dev.koml.core.model.ModelHandle
import dev.koml.core.registry.ModelRegistry

/**
 * Top-level handle to the library's stateful machinery: model registry,
 * downloader, and loaded-session pool. Construct via
 * [dev.koml.engine.LlmKit.initialize].
 *
 * Coordinators are safe to use concurrently from any coroutine — all state
 * is guarded internally.
 */
interface LlmCoordinator {

    /** The registry behind this coordinator (curated list + HF search). */
    val registry: ModelRegistry

    /** The downloader bound to this coordinator's storage + license gate. */
    val downloader: ModelDownloader

    /**
     * Loads a model and returns a session ready to generate. Throws
     * [dev.koml.core.error.KomlException.ModelLoadException] when:
     * - the underlying file at [ModelHandle.localPath] is missing or invalid,
     * - the maximum concurrent session count
     *   ([dev.koml.core.config.LlmKitConfig.maxConcurrentSessions]) has been
     *   reached.
     */
    suspend fun loadModel(
        handle: ModelHandle,
        runtime: RuntimeConfig = RuntimeConfig(),
    ): LlmSession

    /** Snapshot of every currently-loaded session. */
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
