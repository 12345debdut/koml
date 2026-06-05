package dev.koml.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Streams the contents of [path] through a fresh [SHA256] and returns the
 * hex-encoded digest. Reads 64 KB at a time so the entire file never needs
 * to be in memory.
 */
internal suspend fun sha256OfFile(
    path: String,
    fs: FileSystem = systemFs,
): String = withContext(Dispatchers.Default) {
    val hash = SHA256()
    val source = fs.source(path.toPath()).buffer()
    try {
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = source.read(buf)
            if (n == -1) break
            hash.update(buf, 0, n)
        }
    } finally {
        source.close()
    }
    hash.digest().toHexLower()
}

/** Compute the SHA-256 of the in-memory [bytes] and return a lower-hex string. */
internal fun sha256OfBytes(bytes: ByteArray): String {
    val hash = SHA256()
    hash.update(bytes, 0, bytes.size)
    return hash.digest().toHexLower()
}

/** Hex-encode a byte array as a lower-case string. */
internal fun ByteArray.toHexLower(): String {
    val chars = CharArray(size * 2)
    val table = "0123456789abcdef"
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        chars[i * 2]     = table[v ushr 4]
        chars[i * 2 + 1] = table[v and 0x0F]
    }
    return chars.concatToString()
}
