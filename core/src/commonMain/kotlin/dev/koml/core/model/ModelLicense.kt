package dev.koml.core.model

data class ModelLicense(
    val spdxId: String,
    val displayName: String,
    val fullTextUrl: String? = null,
    val termsUrl: String? = null,
    val requiresAcceptance: Boolean = false,
)
