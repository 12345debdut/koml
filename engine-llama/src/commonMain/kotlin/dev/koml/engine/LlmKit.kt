package dev.koml.engine

import dev.koml.core.LlmCoordinator
import dev.koml.core.config.LlmKitConfig
import dev.koml.engine.internal.DefaultLlmCoordinator

object LlmKit {
    suspend fun initialize(config: LlmKitConfig = LlmKitConfig()): LlmCoordinator {
        return DefaultLlmCoordinator(config)
    }
}
