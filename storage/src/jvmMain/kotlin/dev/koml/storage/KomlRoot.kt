package dev.koml.storage

internal actual fun komlRootDir(): String {
    val explicit = System.getenv("KOML_HOME")
    if (!explicit.isNullOrBlank()) return explicit
    val home = System.getProperty("user.home")
        ?: error("user.home not set; cannot resolve Koml storage root")
    return "$home/.koml"
}
