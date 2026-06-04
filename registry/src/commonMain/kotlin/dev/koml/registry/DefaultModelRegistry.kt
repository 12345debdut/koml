package dev.koml.registry

import dev.koml.core.model.ModelInfo
import dev.koml.core.registry.ModelRegistry

/**
 * Real [ModelRegistry] backed by a compile-time list of curated models.
 *
 * - [curated] returns the bundled list.
 * - [searchHuggingFace] is intentionally a stub returning `emptyList()` —
 *   real HF Hub search lands in Phase 3.
 * - [resolve] does a linear lookup over the curated list. With only five
 *   entries this is fine; if we ever add hundreds of models, convert to a
 *   map.
 */
class DefaultModelRegistry : ModelRegistry {

    override suspend fun curated(): List<ModelInfo> = CuratedModels.list

    override suspend fun searchHuggingFace(query: String, ggufOnly: Boolean): List<ModelInfo> =
        emptyList()

    override suspend fun resolve(id: String): ModelInfo? =
        CuratedModels.list.firstOrNull { it.id == id }
}
