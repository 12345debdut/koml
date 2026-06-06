package dev.koml.download

import dev.koml.core.download.DownloadState
import dev.koml.core.error.KomlException
import dev.koml.core.model.ModelInfo
import dev.koml.core.model.ModelLicense
import dev.koml.core.model.PromptTemplate
import dev.koml.core.storage.ModelStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit-test the DefaultModelDownloader against:
 *   - a Ktor MockEngine that serves fake bytes from memory,
 *   - an Okio FakeFileSystem so test runs don't touch real disk,
 *   - a tiny in-memory ModelStorage + LicenseGate.
 *
 * Network-free, disk-free, ~ms per test.
 */
class DefaultModelDownloaderTest {

    private val payload = ByteArray(8 * 1024) { (it and 0xFF).toByte() } // 8 KB body
    private val payloadSha = sha256OfBytes(payload)

    private val testModel = ModelInfo(
        id = "test-model",
        displayName = "Test Model",
        sizeBytes = payload.size.toLong(),
        sha256 = payloadSha,
        downloadUrl = "https://example.test/model.gguf",
        promptTemplate = PromptTemplate.None,
        contextWindow = 2048,
        license = ModelLicense(spdxId = "Apache-2.0", displayName = "Apache 2.0"),
        recommendedRamMb = 256,
    )

    @Test fun happyPath_emitsProgress_thenCompleted() = runTest {
        val (downloader, fs) = newDownloader { request ->
            when (request.method) {
                HttpMethod.Head -> respond(
                    content = ByteArray(0),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentLength to listOf(payload.size.toString()),
                        HttpHeaders.AcceptRanges to listOf("bytes"),
                    ),
                )
                else -> respond(
                    content = payload,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentLength to listOf(payload.size.toString())),
                )
            }
        }

        val states = downloader.download(testModel).toList()
        val completed = states.last() as DownloadState.Completed
        assertEquals("test-model", completed.handle.info.id)
        assertTrue(fs.exists(completed.handle.localPath.toPath()), "final file present")
        assertTrue(!fs.exists("$completed.localPath.part".toPath()), "no .part left behind")
    }

    @Test fun licenseRequired_butNotAccepted_emitsFailed() = runTest {
        val gated = testModel.copy(license = testModel.license.copy(requiresAcceptance = true))
        val (downloader, _) = newDownloader { _ ->
            error("HTTP should never be called when license is not accepted")
        }

        val states = downloader.download(gated).toList()
        val failed = states.single() as DownloadState.Failed
        assertTrue(failed.error is KomlException.LicenseNotAcceptedException)
    }

    @Test fun shaMismatch_deletesPartialAndEmitsFailed() = runTest {
        val tamperedSha = "0".repeat(64)
        val bogus = testModel.copy(sha256 = tamperedSha)

        val (downloader, fs) = newDownloader { request ->
            when (request.method) {
                HttpMethod.Head -> respond(
                    content = ByteArray(0),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentLength to listOf(payload.size.toString()),
                        HttpHeaders.AcceptRanges to listOf("bytes"),
                    ),
                )
                else -> respond(
                    content = payload,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentLength to listOf(payload.size.toString())),
                )
            }
        }

        val states = downloader.download(bogus).toList()
        val failed = states.last() as DownloadState.Failed
        assertTrue(failed.error is KomlException.DownloadException)
        assertTrue(failed.error.message?.contains("SHA-256 mismatch") == true)
        assertTrue(!fs.exists("/test/models/${bogus.id}.gguf.part".toPath()), "partial cleaned up")
    }

    @Test fun alreadyDownloaded_shortCircuitsToCompleted() = runTest {
        val (downloader, fs) = newDownloader { _ ->
            error("HTTP should never be called when final file already verifies")
        }
        // Pre-seed the final file with valid bytes.
        fs.createDirectories("/test/models".toPath())
        // Okio's BufferedSink.use { } lambda is JVM/Android-only because
        // commonTest is shared with iOS/macOS native targets — use try/finally.
        val seed = fs.sink("/test/models/test-model.gguf".toPath()).buffer()
        try { seed.write(payload) } finally { seed.close() }

        val states = downloader.download(testModel).toList()
        assertEquals(1, states.size)
        assertTrue(states.single() is DownloadState.Completed)
    }

    // ── plumbing ────────────────────────────────────────────────

    private fun newDownloader(
        handler: io.ktor.client.engine.mock.MockRequestHandler,
    ): Pair<DefaultModelDownloader, FakeFileSystem> {
        val fakeFs = FakeFileSystem().apply {
            createDirectories("/test".toPath())
        }
        val storage = InMemoryStorage(fakeFs)
        val httpClient = HttpClient(MockEngine(handler)) { followRedirects = true }
        val gate = FileBackedLicenseGate(storage, fakeFs)
        val downloader = DefaultModelDownloader(
            storage = storage,
            httpClient = httpClient,
            licenseGate = gate,
            resolveModel = { if (it == testModel.id) testModel else null },
            fs = fakeFs,
        )
        return downloader to fakeFs
    }
}

/** Minimal ModelStorage backed by a single root path on top of a FakeFileSystem. */
private class InMemoryStorage(private val fs: FileSystem) : ModelStorage {
    private val root = "/test"
    override suspend fun modelsDir(): String = "$root/models"
    override suspend fun modelFile(modelId: String): String = "${modelsDir()}/$modelId.gguf"
    override suspend fun partialFile(modelId: String): String = "${modelsDir()}/$modelId.gguf.part"
    override suspend fun licenseRecord(modelId: String): String = "$root/licenses/$modelId.accepted"
    override suspend fun exists(path: String): Boolean = fs.exists(path.toPath())
    override suspend fun sizeBytes(path: String): Long =
        if (fs.exists(path.toPath())) fs.metadata(path.toPath()).size ?: 0L else 0L
    override suspend fun delete(path: String): Boolean {
        val p = path.toPath()
        if (!fs.exists(p)) return false
        fs.delete(p); return true
    }
    override suspend fun mkdirsForFile(filePath: String) {
        filePath.toPath().parent?.let(fs::createDirectories)
    }
}
