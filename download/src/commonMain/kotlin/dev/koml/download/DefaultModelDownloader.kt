@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.koml.download

import dev.koml.core.download.DownloadState
import dev.koml.core.download.ModelDownloader
import dev.koml.core.error.KomlException
import dev.koml.core.model.ModelHandle
import dev.koml.core.model.ModelInfo
import dev.koml.core.storage.ModelStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.time.TimeSource

/**
 * Default [ModelDownloader] implementation. Streams GGUFs from HTTPS over
 * Ktor, hashes-as-it-writes via kotlincrypto SHA-256, supports resume via
 * HTTP `Range` requests, and atomically renames into place only after the
 * full-file SHA matches the manifest.
 *
 * Flow contract:
 * - If the final file already exists and verifies, emits one [DownloadState.Completed].
 * - If license isn't accepted, emits one [DownloadState.Failed] (LicenseNotAccepted).
 * - Otherwise: zero-or-more [DownloadState.Progress] (throttled to ~4 Hz),
 *   then either [DownloadState.Completed] or [DownloadState.Failed].
 *
 * The whole flow runs on a bounded dispatcher (`Default.limitedParallelism(2)`)
 * so simultaneous downloads don't starve other coroutines.
 */
class DefaultModelDownloader internal constructor(
    private val storage: ModelStorage,
    private val httpClient: HttpClient,
    private val licenseGate: LicenseGate,
    private val resolveModel: suspend (String) -> ModelInfo?,
    private val fs: FileSystem = systemFs,
) : ModelDownloader {

    override fun download(model: ModelInfo): Flow<DownloadState> = flow {
        // Fast path: already-downloaded and verified.
        val finalPath = storage.modelFile(model.id)
        if (storage.exists(finalPath) && sha256OfFile(finalPath, fs) == model.sha256) {
            emit(DownloadState.Completed(ModelHandle(model, finalPath)))
            return@flow
        }

        // License gate.
        if (model.license.requiresAcceptance && !licenseGate.isAccepted(model.id)) {
            emit(
                DownloadState.Failed(
                    KomlException.LicenseNotAcceptedException(
                        "License '${model.license.spdxId}' for ${model.id} not accepted. " +
                            "Call coordinator.acceptLicense(\"${model.id}\") first.",
                    ),
                ),
            )
            return@flow
        }

        val partPath = storage.partialFile(model.id)
        storage.mkdirsForFile(partPath)

        // Decide where to resume from. If the partial is somehow larger than
        // the announced size, throw it out — something else wrote there.
        val resumeFrom: Long = if (storage.exists(partPath)) {
            val size = storage.sizeBytes(partPath)
            if (size >= model.sizeBytes) {
                storage.delete(partPath); 0L
            } else size
        } else 0L

        // HEAD request to verify server still has the same file at the same size.
        val head = httpClient.head(model.downloadUrl)
        val acceptsRanges = head.headers[HttpHeaders.AcceptRanges] == "bytes"
        val serverSize = head.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (serverSize != null && serverSize != model.sizeBytes) {
            emit(
                DownloadState.Failed(
                    KomlException.DownloadException(
                        "Server announces $serverSize bytes for ${model.id}; manifest expects ${model.sizeBytes}",
                    ),
                ),
            )
            return@flow
        }

        try {
            httpClient.prepareGet(model.downloadUrl) {
                if (resumeFrom > 0 && acceptsRanges) {
                    headers { append(HttpHeaders.Range, "bytes=$resumeFrom-") }
                }
            }.execute { response ->
                // Server may ignore our Range and respond 200; restart from 0 if so.
                val effectiveStart = if (response.status == HttpStatusCode.PartialContent) resumeFrom else 0L
                if (effectiveStart == 0L && storage.exists(partPath)) {
                    storage.delete(partPath)
                }

                val sha = SHA256()
                // If resuming, re-hash existing partial so the final digest covers the whole file.
                if (effectiveStart > 0) {
                    val src = fs.source(partPath.toPath()).buffer()
                    try {
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = src.read(buf)
                            if (n == -1) break
                            sha.update(buf, 0, n)
                        }
                    } finally {
                        src.close()
                    }
                }

                val sinkPath = partPath.toPath()
                val rawSink = if (effectiveStart > 0) fs.appendingSink(sinkPath) else fs.sink(sinkPath)
                val buffered = rawSink.buffer()
                try {
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    val buf = ByteArray(64 * 1024)
                    var downloaded = effectiveStart
                    val total = model.sizeBytes
                    var lastEmit = TimeSource.Monotonic.markNow()
                    var bytesSinceLast = 0L

                    while (!channel.isClosedForRead) {
                        val n = channel.readAvailable(buf, 0, buf.size)
                        if (n <= 0) break
                        sha.update(buf, 0, n)
                        buffered.write(buf, 0, n)
                        downloaded += n
                        bytesSinceLast += n

                        val now = TimeSource.Monotonic.markNow()
                        val elapsedMs = (now - lastEmit).inWholeMilliseconds
                        if (elapsedMs >= PROGRESS_EMIT_INTERVAL_MS) {
                            val bps = if (elapsedMs > 0) bytesSinceLast * 1000 / elapsedMs else 0L
                            emit(DownloadState.Progress(downloaded, total, bps))
                            lastEmit = now
                            bytesSinceLast = 0L
                        }
                    }
                    buffered.flush()
                } finally {
                    buffered.close()
                }

                val computed = sha.digest().toHexLower()
                if (computed != model.sha256) {
                    storage.delete(partPath)
                    emit(
                        DownloadState.Failed(
                            KomlException.DownloadException(
                                "SHA-256 mismatch for ${model.id}: expected ${model.sha256}, got $computed",
                            ),
                        ),
                    )
                    return@execute
                }

                fs.atomicMove(partPath.toPath(), finalPath.toPath())
                emit(DownloadState.Completed(ModelHandle(model, finalPath)))
            }
        } catch (e: KomlException) {
            emit(DownloadState.Failed(e))
        } catch (e: Throwable) {
            emit(
                DownloadState.Failed(
                    KomlException.DownloadException("Download of ${model.id} failed: ${e.message}", e),
                ),
            )
        }
    }.flowOn(Dispatchers.Default.limitedParallelism(2))

    override suspend fun isDownloaded(id: String): Boolean {
        val info = resolveModel(id) ?: return false
        val path = storage.modelFile(id)
        return storage.exists(path) && sha256OfFile(path, fs) == info.sha256
    }

    override suspend fun delete(id: String): Boolean {
        val path = storage.modelFile(id)
        val partPath = storage.partialFile(id)
        val a = storage.delete(path)
        val b = storage.delete(partPath)
        return a || b
    }

    override suspend fun localModels(): List<ModelHandle> = withListedFiles { files ->
        files.mapNotNull { fileName ->
            if (!fileName.endsWith(".gguf")) return@mapNotNull null
            val id = fileName.removeSuffix(".gguf")
            val info = resolveModel(id) ?: return@mapNotNull null
            ModelHandle(info, "${storage.modelsDir()}/$fileName")
        }
    }

    private suspend fun <T> withListedFiles(block: suspend (List<String>) -> T): T {
        val dir = storage.modelsDir().toPath()
        if (!fs.exists(dir)) return block(emptyList())
        val names = fs.list(dir).map { it.name }
        return block(names)
    }

    companion object {
        /** Throttle progress emissions to ~4 Hz so UI consumers don't recompose on every chunk. */
        private const val PROGRESS_EMIT_INTERVAL_MS = 250L
    }
}
