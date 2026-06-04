package dev.koml.storage

internal actual fun komlRootDir(): String {
    val ctx = KomlContext.appContextOrNull
        ?: error(
            "Koml storage was used before the application context was captured. " +
                "androidx.startup should have done this automatically. " +
                "If you removed the provider, call KomlContext.installManually(context) " +
                "from your Application.onCreate().",
        )
    return "${ctx.filesDir.absolutePath}/koml"
}
