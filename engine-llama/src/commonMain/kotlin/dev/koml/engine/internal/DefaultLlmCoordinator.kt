package dev.koml.engine.internal

import dev.koml.core.LlmCoordinator
import dev.koml.core.LlmSession
import dev.koml.core.config.LlmKitConfig
import dev.koml.core.config.RuntimeConfig
import dev.koml.core.download.ModelDownloader
import dev.koml.core.error.KomlException
import dev.koml.core.model.ModelHandle
import dev.koml.core.registry.ModelRegistry
import dev.koml.engine.LlamaNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class DefaultLlmCoordinator(
    private val config: LlmKitConfig,
) : LlmCoordinator {

    private val native: LlamaNative = LlamaNative().also { it.initBackend() }

    private val sessionsMutex = Mutex()
    private val sessions = mutableListOf<LlmSession>()

    override val registry: ModelRegistry = StubRegistry()
    override val downloader: ModelDownloader = StubDownloader()

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun loadModel(handle: ModelHandle, runtime: RuntimeConfig): LlmSession {
        sessionsMutex.withLock {
            if (sessions.size >= config.maxConcurrentSessions) {
                throw KomlException.ModelLoadException(
                    "Maximum concurrent sessions (${config.maxConcurrentSessions}) reached"
                )
            }
        }

        val sessionDispatcher = Dispatchers.Default.limitedParallelism(1)

        val session = withContext(sessionDispatcher) {
            val modelPtr = native.loadModel(handle.localPath)
            val contextPtr = try {
                native.createContext(modelPtr, runtime.contextSize)
            } catch (e: Throwable) {
                native.freeModel(modelPtr)
                throw KomlException.ModelLoadException(
                    "Failed to create context: ${e.message}", e
                )
            }

            DefaultLlmSession(
                native = native,
                handle = handle,
                runtime = runtime,
                modelPtr = modelPtr,
                contextPtr = contextPtr,
                sessionDispatcher = sessionDispatcher,
                onUnload = { closed -> removeSession(closed) },
            )
        }

        sessionsMutex.withLock { sessions.add(session) }
        return session
    }

    override suspend fun activeSessions(): List<LlmSession> = sessionsMutex.withLock {
        sessions.toList()
    }

    private suspend fun removeSession(session: LlmSession) {
        sessionsMutex.withLock { sessions.remove(session) }
    }
}
