package dev.koml.core.registry

import dev.koml.core.model.ModelInfo

/**
 * Catalogue of models the user can pick from. Implementations may combine
 * a bundled curated list with on-demand HuggingFace Hub search.
 */
interface ModelRegistry {

    /**
     * The curated, fully-specified list of models shipped with the library.
     * Each entry's [ModelInfo.downloadUrl] and [ModelInfo.sha256] are filled
     * in, so passing one to [dev.koml.core.download.ModelDownloader.download]
     * just works.
     */
    suspend fun curated(): List<ModelInfo>

    /**
     * Best-effort search of HuggingFace Hub. Returned [ModelInfo] objects
     * carry the repo id and basic metadata but **not** the per-file
     * download URL or SHA-256 — callers wanting to download a search hit
     * must either supplement those fields themselves OR use
     * [searchHuggingFaceWithDetails] which fans out one extra request per
     * result.
     *
     * Returns an empty list when the underlying implementation has no HF
     * client wired or when HF rate-limits / errors out.
     */
    suspend fun searchHuggingFace(query: String, ggufOnly: Boolean = true): List<ModelInfo>

    /**
     * Same as [searchHuggingFace] but additionally fetches the file list for
     * each matching repo, populating [ModelInfo.downloadUrl] and (when HF
     * exposes it) [ModelInfo.sha256]. The first `.gguf` file in each repo's
     * siblings is picked.
     *
     * **Cost:** one extra HTTP round-trip per search result. A search of 20
     * results is 21 API hits (the search + 20 file-list calls), executed
     * sequentially. Use sparingly; prefer caching at the call site if you
     * search the same term repeatedly.
     *
     * The default implementation delegates to [searchHuggingFace] without
     * adding file details — implementations that lack an HTTP client just
     * fall back gracefully.
     */
    suspend fun searchHuggingFaceWithDetails(
        query: String,
        ggufOnly: Boolean = true,
    ): List<ModelInfo> = searchHuggingFace(query, ggufOnly)

    /**
     * Looks up a [ModelInfo] by id from the curated list. Returns `null`
     * for unknown ids; search results are *not* resolved this way (use
     * the result from [searchHuggingFace] directly).
     */
    suspend fun resolve(id: String): ModelInfo?
}
