package dev.koml.engine.internal

import dev.koml.core.model.ModelInfo
import dev.koml.core.registry.ModelRegistry

internal class StubRegistry : ModelRegistry {
    override suspend fun curated(): List<ModelInfo> = emptyList()
    override suspend fun searchHuggingFace(query: String, ggufOnly: Boolean): List<ModelInfo> = emptyList()
    override suspend fun resolve(id: String): ModelInfo? = null
}
