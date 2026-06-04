package dev.koml.download

import dev.koml.core.download.ModelDownloader
import dev.koml.core.model.ModelInfo
import dev.koml.core.storage.ModelStorage

/**
 * Bundle of the downloader and the license-acceptance gate that backs it.
 * Both share the same [LicenseGate] instance so an acceptance recorded via
 * [acceptLicense] takes effect for the next [ModelDownloader.download] call.
 */
class ModelDownloadStack internal constructor(
    val downloader: ModelDownloader,
    private val gate: LicenseGate,
) {
    /** Records license acceptance for [modelId]. */
    suspend fun acceptLicense(modelId: String) = gate.accept(modelId)

    /** Returns true if acceptance for [modelId] was previously recorded. */
    suspend fun isLicenseAccepted(modelId: String): Boolean = gate.isAccepted(modelId)
}

object ModelDownloaderFactory {
    /**
     * Wires up a [ModelDownloadStack] using the platform-default Ktor engine
     * (OkHttp on Android/JVM, Darwin on iOS), a file-backed license gate
     * rooted at [storage]'s `licenses/` directory, and the provided
     * [resolveModel] lookup for `localModels()` / `isDownloaded()`.
     */
    fun create(
        storage: ModelStorage,
        resolveModel: suspend (String) -> ModelInfo?,
    ): ModelDownloadStack {
        val httpClient = defaultHttpClient()
        val gate = FileBackedLicenseGate(storage)
        val downloader = DefaultModelDownloader(storage, httpClient, gate, resolveModel)
        return ModelDownloadStack(downloader, gate)
    }
}
