package dev.koml.core.model

/**
 * License metadata for a model. Surface this in download UIs so users see
 * what they're agreeing to before bytes start flowing.
 *
 * @property spdxId SPDX identifier (e.g. `Apache-2.0`, `MIT`,
 *   `LicenseRef-LlamaCommunity`). `UNKNOWN` is the placeholder used by
 *   HuggingFace search results until the caller inspects the repo.
 * @property displayName user-friendly license name.
 * @property fullTextUrl HTTPS URL to the canonical license text (may be
 *   `null` if not known).
 * @property termsUrl HTTPS URL to additional terms beyond the license
 *   itself (e.g. acceptable-use policy). Often `null`.
 * @property requiresAcceptance when `true`,
 *   [dev.koml.core.download.ModelDownloader.download] refuses to start
 *   until [dev.koml.core.LlmCoordinator.acceptLicense] has been called
 *   for the model id.
 */
data class ModelLicense(
    val spdxId: String,
    val displayName: String,
    val fullTextUrl: String? = null,
    val termsUrl: String? = null,
    val requiresAcceptance: Boolean = false,
)
