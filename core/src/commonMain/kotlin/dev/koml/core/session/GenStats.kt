package dev.koml.core.session

data class GenStats(
    val promptTokens: Int,
    val generatedTokens: Int,
    val promptEvalMs: Long,
    val generateMs: Long,
    val tokensPerSecond: Double,
)
