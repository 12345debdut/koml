package dev.koml.core.error

sealed class KomlException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    class ModelLoadException(message: String, cause: Throwable? = null) : KomlException(message, cause)

    class InferenceException(message: String, cause: Throwable? = null) : KomlException(message, cause)

    class DownloadException(message: String, cause: Throwable? = null) : KomlException(message, cause)

    class StorageException(message: String, cause: Throwable? = null) : KomlException(message, cause)

    class EngineNotAvailableException(message: String, cause: Throwable? = null) : KomlException(message, cause)

    class LicenseNotAcceptedException(message: String, cause: Throwable? = null) : KomlException(message, cause)
}
