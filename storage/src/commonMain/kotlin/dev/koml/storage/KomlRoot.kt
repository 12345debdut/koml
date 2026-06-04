package dev.koml.storage

/**
 * Platform-resolved absolute path to the Koml storage root.
 *
 * - Android: `<context.filesDir>/koml`
 * - iOS:     `<NSDocumentDirectory>/koml`
 * - JVM:     `$KOML_HOME` if set, else `~/.koml`
 *
 * Internal because consumers should go through [ModelStorageFactory.create] /
 * [DefaultModelStorage] for the full layout, not the raw root.
 */
internal expect fun komlRootDir(): String
