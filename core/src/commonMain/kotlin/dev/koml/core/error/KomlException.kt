package dev.koml.core.error

/**
 * Root of every error Koml raises. Sealed so you can exhaustively `when`
 * on the subtypes in error-handling code.
 *
 * Use `try { ... } catch (e: KomlException) { ... }` to catch every Koml
 * error at once, or catch the specific subclass when you can recover
 * differently per failure mode.
 */
sealed class KomlException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /**
     * Model file couldn't be loaded — missing, corrupt, wrong arch, or the
     * `llama.cpp` runtime refused it. Often the underlying [cause] is the
     * native error.
     */
    class ModelLoadException(message: String, cause: Throwable? = null) : KomlException(message, cause)

    /**
     * Something went wrong during generation — tokenisation, decode, or
     * sampling failed. Recovery usually means unloading the session and
     * starting fresh.
     */
    class InferenceException(message: String, cause: Throwable? = null) : KomlException(message, cause)

    /**
     * Download didn't complete: network error, SHA-256 mismatch, server
     * returned wrong size, etc. The message is always specific about which
     * of these happened.
     */
    class DownloadException(message: String, cause: Throwable? = null) : KomlException(message, cause)

    /**
     * The platform's filesystem refused an operation Koml needs (read,
     * write, mkdir, atomic rename). Disk full / permissions are typical.
     */
    class StorageException(message: String, cause: Throwable? = null) : KomlException(message, cause)

    /**
     * No native engine is wired for the current platform — usually means
     * the JVM JNI lib for this OS/arch wasn't packaged. See
     * `docs/known-issues.md#7` for the Linux/Windows recipe.
     */
    class EngineNotAvailableException(message: String, cause: Throwable? = null) : KomlException(message, cause)

    /**
     * Tried to download a model whose [dev.koml.core.model.ModelLicense.requiresAcceptance]
     * is `true` without first calling
     * [dev.koml.core.LlmCoordinator.acceptLicense]. The message includes the
     * model id and the exact `acceptLicense(...)` call to make.
     */
    class LicenseNotAcceptedException(message: String, cause: Throwable? = null) : KomlException(message, cause)
}
