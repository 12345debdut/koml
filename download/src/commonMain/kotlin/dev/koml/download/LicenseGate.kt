package dev.koml.download

import dev.koml.core.storage.ModelStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Records and queries per-model license acceptance. Backed by empty marker
 * files at [ModelStorage.licenseRecord] so state survives process restarts.
 */
internal interface LicenseGate {
    suspend fun isAccepted(modelId: String): Boolean
    suspend fun accept(modelId: String)
}

internal class FileBackedLicenseGate(
    private val storage: ModelStorage,
    private val fs: FileSystem = systemFs,
) : LicenseGate {

    override suspend fun isAccepted(modelId: String): Boolean =
        storage.exists(storage.licenseRecord(modelId))

    override suspend fun accept(modelId: String) = withContext(Dispatchers.Default) {
        val path = storage.licenseRecord(modelId)
        storage.mkdirsForFile(path)
        fs.write(path.toPath()) {
            // empty marker; presence is the signal
        }
    }
}
