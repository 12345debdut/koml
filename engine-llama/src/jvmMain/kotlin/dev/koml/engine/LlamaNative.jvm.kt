package dev.koml.engine

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal actual class LlamaNative actual constructor() {

    actual external fun initBackend()
    actual external fun loadModel(path: String): Long
    actual external fun freeModel(handle: Long)
    actual external fun createContext(modelHandle: Long, contextSize: Int): Long
    actual external fun freeContext(ctxHandle: Long)
    actual external fun tokenize(modelHandle: Long, text: String, addBos: Boolean): IntArray
    actual external fun decode(ctxHandle: Long, tokens: IntArray): Boolean
    actual external fun sampleToken(ctxHandle: Long, temperature: Float, topP: Float, topK: Int): Int
    actual external fun tokenToPiece(modelHandle: Long, token: Int): String
    actual external fun isEogToken(modelHandle: Long, token: Int): Boolean

    companion object {
        @Volatile private var loaded = false
        private val loadLock = Any()

        init { ensureLoaded() }

        /**
         * Extracts the right `libkoml-jni.dylib` slice from the JAR (under
         * `META-INF/native/<arch>/`) into a temp dir and `System.load`s it.
         * Idempotent — safe to call any number of times; the actual
         * `System.load` runs at most once per JVM process.
         */
        fun ensureLoaded() {
            if (loaded) return
            synchronized(loadLock) {
                if (loaded) return

                val arch = detectArch()
                val resourcePath = "/META-INF/native/$arch/libkoml-jni.dylib"
                val stream = LlamaNative::class.java.getResourceAsStream(resourcePath)
                    ?: throw UnsatisfiedLinkError(
                        "Native lib not packaged for $arch (looked for $resourcePath). " +
                            "Run scripts/build-llama-jvm.sh first.",
                    )

                val tmpDir = Files.createTempDirectory("koml-").also {
                    it.toFile().deleteOnExit()
                }
                val tmpFile: File = tmpDir.resolve("libkoml-jni.dylib").toFile().also {
                    it.deleteOnExit()
                }
                stream.use { src ->
                    Files.copy(src, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }

                System.load(tmpFile.absolutePath)
                loaded = true
            }
        }

        private fun detectArch(): String {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            require(os.contains("mac") || os.contains("darwin")) {
                "Koml's JVM engine only ships macOS binaries in v0.0.2. " +
                    "For Linux/Windows, build from source: scripts/build-llama-jvm.sh"
            }
            return when (arch) {
                "aarch64", "arm64" -> "macos-arm64"
                "x86_64", "amd64" -> "macos-x64"
                else -> error("Unsupported macOS arch: $arch")
            }
        }
    }
}
