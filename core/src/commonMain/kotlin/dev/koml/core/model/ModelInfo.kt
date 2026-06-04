package dev.koml.core.model

data class ModelInfo(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
    val promptTemplate: PromptTemplate,
    val contextWindow: Int,
    val license: ModelLicense,
    val recommendedRamMb: Int,
)
