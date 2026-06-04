package dev.koml.storage

import okio.FileSystem

/**
 * Okio's `FileSystem.SYSTEM` isn't accessible at the commonMain metadata
 * compile boundary even though every actual target we ship (JVM, Android,
 * iOS) provides it. This expect/actual is a small wrapper so commonMain
 * code can reference a real filesystem without tripping the metadata
 * compiler.
 */
internal expect val systemFs: FileSystem
