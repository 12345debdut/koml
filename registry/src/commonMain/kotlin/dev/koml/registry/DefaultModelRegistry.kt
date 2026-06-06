package dev.koml.registry

import dev.koml.core.model.ModelInfo
import dev.koml.core.registry.ModelRegistry
import io.ktor.client.HttpClient

/**
 * Real [ModelRegistry] backed by a compile-time list of curated models plus
 * (optionally) the HuggingFace Hub search API for discovery.
 *
 * - [curated] returns the bundled list of 5 ungated models.
 * - [searchHuggingFace] hits HuggingFace's public `/api/models` endpoint when
 *   an [HttpClient] was supplied; returns metadata-only results (no SHA-256
 *   or download URL — those require additional per-repo calls deferred to
 *   a later release). Returns an empty list if no client was wired.
 * - [resolve] does a linear lookup over the curated list. With only five
 *   entries this is fine; if we ever add hundreds of models, convert to a
 *   map.
 *
 * Construct via [DefaultModelRegistryFactory.create] for the standard wiring
 * (HF search enabled with a platform-default HTTP client).
 */
class DefaultModelRegistry internal constructor(
    private val searcher: HuggingFaceSearcher?,
) : ModelRegistry {

    /** Convenience constructor for callers who don't want HF search. */
    constructor() : this(searcher = null)

    override suspend fun curated(): List<ModelInfo> = CuratedModels.list

    override suspend fun searchHuggingFace(query: String, ggufOnly: Boolean): List<ModelInfo> =
        searcher?.search(query, ggufOnly).orEmpty()

    override suspend fun searchHuggingFaceWithDetails(
        query: String,
        ggufOnly: Boolean,
    ): List<ModelInfo> =
        searcher?.searchWithDetails(query, ggufOnly).orEmpty()

    override suspend fun resolve(id: String): ModelInfo? =
        CuratedModels.list.firstOrNull { it.id == id }
}

object DefaultModelRegistryFactory {
    /**
     * Returns a [ModelRegistry] backed by the bundled curated list and HF
     * Hub search via a platform-default Ktor [HttpClient].
     *
     * Pass [enableHuggingFaceSearch] = false (or supply [httpClient] = null
     * directly) to ship a registry that only exposes the curated list.
     */
    fun create(
        enableHuggingFaceSearch: Boolean = true,
        httpClient: HttpClient? = if (enableHuggingFaceSearch) defaultRegistryHttpClient() else null,
    ): ModelRegistry {
        val searcher = httpClient?.let(::HuggingFaceSearcher)
        return DefaultModelRegistry(searcher)
    }
}
