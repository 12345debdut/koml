package dev.koml.storage

import dev.koml.core.storage.ModelStorage

/**
 * Entry point for obtaining a [ModelStorage] instance bound to the current
 * platform's default root directory.
 *
 * Internally resolves through [komlRootDir]. On Android this requires the
 * application context to have been captured by `KomlContextInitializer` (the
 * `androidx.startup` provider does this automatically at process start; users
 * who strip that provider must call `KomlContext.installManually(context)`
 * from their `Application.onCreate()`).
 */
object ModelStorageFactory {
    fun create(): ModelStorage = DefaultModelStorage(::komlRootDir, systemFs)
}
