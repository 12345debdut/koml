@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.koml.storage

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal actual fun komlRootDir(): String {
    val urls = NSFileManager.defaultManager.URLsForDirectory(
        NSDocumentDirectory,
        NSUserDomainMask,
    )
    val documents = urls.firstOrNull() as? NSURL
        ?: error("Unable to resolve NSDocumentDirectory")
    val path = documents.path
        ?: error("NSDocumentDirectory URL has no filesystem path")
    return "$path/koml"
}
