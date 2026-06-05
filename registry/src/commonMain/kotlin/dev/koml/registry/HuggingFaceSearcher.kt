package dev.koml.registry

import dev.koml.core.model.ModelInfo
import dev.koml.core.model.ModelLicense
import dev.koml.core.model.PromptTemplate
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * Best-effort wrapper over the public HuggingFace Hub API.
 *
 * v0.0.3 returns "discoverable but not directly downloadable" [ModelInfo]
 * entries: the repo id and basic metadata are populated, but [ModelInfo.sha256]
 * and [ModelInfo.downloadUrl] are blank since the file-list endpoint is a
 * separate per-repo call we deliberately avoid (N+1 round-trips would make
 * a search of 20 results = 21 API hits). A future release may add an opt-in
 * `withFileDetails = true` flag that fans out per result.
 *
 * The API itself is anonymous — no token required for public repos.
 * Rate limit is roughly 1000 requests / hour / IP at the time of writing.
 */
internal class HuggingFaceSearcher(private val httpClient: HttpClient) {

    suspend fun search(query: String, ggufOnly: Boolean, limit: Int = 20): List<ModelInfo> {
        val response = httpClient.get(API_ENDPOINT) {
            parameter("search", query)
            if (ggufOnly) parameter("filter", "gguf")
            parameter("sort", "downloads")
            parameter("direction", "-1")
            parameter("limit", limit.coerceIn(1, 100))
        }
        if (!response.status.isSuccess()) {
            // 429 = rate limited; 5xx = HF server problems. Return empty rather than throw,
            // since search is best-effort and callers should still see the curated list.
            return emptyList()
        }
        val raw: List<HfModelDto> = try {
            response.body()
        } catch (_: Throwable) {
            return emptyList()
        }
        return raw.map { it.toModelInfo() }
    }

    private fun HfModelDto.toModelInfo(): ModelInfo {
        val repoId = id ?: modelId ?: return placeholderModelInfo()
        return ModelInfo(
            id = repoId,
            displayName = repoId,
            sizeBytes = 0L,                              // unknown without file-list call
            sha256 = "",                                 // unknown
            downloadUrl = "",                            // unknown without file-list call
            promptTemplate = PromptTemplate.None,        // can't infer reliably from tags
            contextWindow = 2048,                        // sensible default; HF doesn't report this
            license = ModelLicense(
                spdxId = "UNKNOWN",
                displayName = "Unknown (check repo card)",
                fullTextUrl = "https://huggingface.co/$repoId",
                termsUrl = null,
                requiresAcceptance = false,
            ),
            recommendedRamMb = 1_024,
        )
    }

    private fun placeholderModelInfo(): ModelInfo = ModelInfo(
        id = "unknown",
        displayName = "Unknown",
        sizeBytes = 0L,
        sha256 = "",
        downloadUrl = "",
        promptTemplate = PromptTemplate.None,
        contextWindow = 2048,
        license = ModelLicense(spdxId = "UNKNOWN", displayName = "Unknown"),
        recommendedRamMb = 1_024,
    )

    companion object {
        private const val API_ENDPOINT = "https://huggingface.co/api/models"
    }
}

@Serializable
internal data class HfModelDto(
    val id: String? = null,
    val modelId: String? = null,
    val downloads: Long = 0,
    val likes: Long = 0,
    val tags: List<String> = emptyList(),
    val pipeline_tag: String? = null,
    val lastModified: String? = null,
)
