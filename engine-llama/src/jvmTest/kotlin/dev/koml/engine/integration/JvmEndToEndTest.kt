package dev.koml.engine.integration

import dev.koml.core.config.RuntimeConfig
import dev.koml.core.download.DownloadState
import dev.koml.core.session.GenParams
import dev.koml.core.session.TokenEvent
import dev.koml.engine.LlmKit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertTrue

/**
 * End-to-end JVM test: registry → download → load → generate.
 * Skipped by default — costs ~2 min and ~145 MB of network. Run via:
 *
 *     KOML_INTEGRATION_TESTS=1 ./gradlew :engine-llama:jvmTest \
 *         --tests "dev.koml.engine.integration.*"
 *
 * Prerequisites:
 *
 * - `./scripts/build-llama-jvm.sh` has produced the macOS native libs.
 *   Without them, `System.load(libkoml-jni.dylib)` throws and the test
 *   fails fast.
 * - Outbound HTTPS to huggingface.co + cdn-lfs.huggingface.co.
 * - The curated SHA-256 for smollm2-135m-instruct-q8 is populated — if
 *   it's still the TODO placeholder, the download will fail SHA verification
 *   (and that's a useful signal: time to run scripts/refresh-manifest-shas.sh).
 *
 * Asserts the *plumbing* works end-to-end; does not check generation quality.
 */
class JvmEndToEndTest {

    @Test
    fun searchDownloadLoadGenerate_smollm2_135m() = runBlocking {
        assumeTrue(
            "Set KOML_INTEGRATION_TESTS=1 to run this end-to-end test",
            System.getenv("KOML_INTEGRATION_TESTS") == "1",
        )

        val coordinator = LlmKit.initialize()

        val model = coordinator.registry.curated()
            .firstOrNull { it.id == "smollm2-135m-instruct-q8" }
            ?: error("smollm2-135m-instruct-q8 missing from curated registry")

        val final = coordinator.downloader.download(model).toList().last()
        val handle = when (final) {
            is DownloadState.Completed -> final.handle
            is DownloadState.Failed -> error("download failed: ${final.error.message}")
            else -> error("unexpected terminal state: $final")
        }

        val session = coordinator.loadModel(handle, RuntimeConfig(contextSize = 512))
        try {
            val events = session.generate("Hello", GenParams(maxTokens = 5)).toList()
            val tokens = events.filterIsInstance<TokenEvent.Token>()
            val errors = events.filterIsInstance<TokenEvent.Error>()

            assertTrue(errors.isEmpty(), "generation errors: ${errors.map { it.cause.message }}")
            assertTrue(tokens.isNotEmpty(), "expected at least one token; got Done immediately")
            assertTrue(tokens.any { it.text.isNotEmpty() }, "every token had empty text")
        } finally {
            session.unload()
        }
    }
}
