package dev.koml.storage

import dev.koml.core.storage.ModelStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Filesystem-backed [ModelStorage] using Okio's [FileSystem.SYSTEM].
 *
 * All platform variance is isolated to the [rootProvider] (typically
 * [komlRootDir]). All other path math is pure Kotlin string joining; tests
 * can supply a temp-dir provider.
 */
class DefaultModelStorage internal constructor(
    private val rootProvider: () -> String,
    private val fs: FileSystem,
) : ModelStorage {

    private suspend fun root(): String = withContext(Dispatchers.Default) { rootProvider() }

    override suspend fun modelsDir(): String = "${root()}/models"

    override suspend fun modelFile(modelId: String): String =
        "${modelsDir()}/${modelId}.gguf"

    override suspend fun partialFile(modelId: String): String =
        "${modelsDir()}/${modelId}.gguf.part"

    override suspend fun licenseRecord(modelId: String): String =
        "${root()}/licenses/${modelId}.accepted"

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.Default) {
        fs.exists(path.toPath())
    }

    override suspend fun sizeBytes(path: String): Long = withContext(Dispatchers.Default) {
        if (!fs.exists(path.toPath())) 0L
        else fs.metadata(path.toPath()).size ?: 0L
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.Default) {
        val p = path.toPath()
        if (!fs.exists(p)) {
            false
        } else {
            fs.delete(p)
            true
        }
    }

    override suspend fun mkdirsForFile(filePath: String) = withContext(Dispatchers.Default) {
        val parent = filePath.toPath().parent ?: return@withContext
        fs.createDirectories(parent)
    }
}
