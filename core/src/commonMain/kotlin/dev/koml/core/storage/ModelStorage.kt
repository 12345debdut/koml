package dev.koml.core.storage

/**
 * Platform-agnostic abstraction over the filesystem locations Koml uses to
 * persist downloaded models and license-acceptance records.
 *
 * Implementations are expected to be safe to call from any coroutine; all
 * suspending methods perform I/O. The concrete implementation in `:storage`
 * resolves paths to:
 *
 * - Android: `<context.filesDir>/koml/...`
 * - iOS:     `<NSDocumentDirectory>/koml/...`
 * - JVM:     `$KOML_HOME` or `~/.koml/...`
 *
 * File layout under the platform root:
 *
 * ```
 * <root>/models/<modelId>.gguf          # final, verified
 * <root>/models/<modelId>.gguf.part     # partial download (atomic-renamed on verify)
 * <root>/licenses/<modelId>.accepted    # empty marker; presence ⇒ license accepted
 * ```
 */
interface ModelStorage {

    /** Absolute path to the directory holding final, verified model files. */
    suspend fun modelsDir(): String

    /** Absolute path to the final on-disk file for [modelId]. */
    suspend fun modelFile(modelId: String): String

    /**
     * Absolute path to the in-progress download file for [modelId]. Always in
     * the same directory as [modelFile] so atomic rename works on every
     * platform.
     */
    suspend fun partialFile(modelId: String): String

    /** Absolute path to the license-acceptance marker file for [modelId]. */
    suspend fun licenseRecord(modelId: String): String

    /** Returns `true` if a file or directory exists at [path]. */
    suspend fun exists(path: String): Boolean

    /** Size of the file at [path] in bytes, or `0` if it does not exist. */
    suspend fun sizeBytes(path: String): Long

    /** Deletes the file at [path]. Returns `true` if a file was removed. */
    suspend fun delete(path: String): Boolean

    /**
     * Ensures all parent directories of [filePath] exist. No-op if they
     * already do.
     */
    suspend fun mkdirsForFile(filePath: String)
}
