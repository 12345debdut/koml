package dev.koml.core.model

/**
 * Static metadata describing a downloadable GGUF model.
 *
 * @property id stable identifier; used as the file name on disk and as the
 *   key for [dev.koml.core.registry.ModelRegistry.resolve] / acceptance
 *   records.
 * @property displayName human-readable name shown in UIs.
 * @property sizeBytes expected file size; checked against the HTTP
 *   `Content-Length` before download starts.
 * @property sha256 lower-case hex digest of the full file. Verified
 *   incrementally as bytes arrive; mismatch aborts the download and
 *   removes the partial file.
 * @property downloadUrl direct HTTPS URL to the GGUF (no auth, redirects
 *   are followed). HuggingFace `resolve/main/...` URLs work as-is.
 * @property promptTemplate the chat template family this model expects.
 *   Drives [dev.koml.core.LlmSession.chat] formatting.
 * @property contextWindow the model's native context window in tokens;
 *   acts as an upper bound for [dev.koml.core.config.RuntimeConfig.contextSize].
 * @property license license metadata + acceptance requirement.
 * @property recommendedRamMb conservative RAM estimate the model needs to
 *   load + run with default settings. Use as a UI hint, not a hard check.
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
    val promptTemplate: PromptTemplate,
    val contextWindow: Int,
    val license: ModelLicense,
    val recommendedRamMb: Int,
)
