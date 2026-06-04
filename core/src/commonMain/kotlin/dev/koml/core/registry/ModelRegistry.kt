package dev.koml.core.registry

import dev.koml.core.model.ModelInfo

interface ModelRegistry {
    suspend fun curated(): List<ModelInfo>

    suspend fun searchHuggingFace(query: String, ggufOnly: Boolean = true): List<ModelInfo>

    suspend fun resolve(id: String): ModelInfo?
}
