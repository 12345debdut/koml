package dev.koml.storage

import android.content.Context
import androidx.startup.Initializer

/**
 * Singleton holder for the application context. Populated automatically at
 * process start by [KomlContextInitializer] (via the androidx.startup
 * `InitializationProvider`) so user code never has to thread a Context
 * through Koml's API surface.
 */
object KomlContext {
    @Volatile
    private var _appContext: Context? = null

    internal val appContextOrNull: Context? get() = _appContext

    /**
     * Escape hatch for apps that disable androidx.startup. Call from your
     * `Application.onCreate()` before any Koml API use.
     */
    fun installManually(context: Context) {
        _appContext = context.applicationContext
    }

    internal fun set(context: Context) {
        _appContext = context.applicationContext
    }
}

class KomlContextInitializer : Initializer<KomlContext> {
    override fun create(context: Context): KomlContext {
        KomlContext.set(context)
        return KomlContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
