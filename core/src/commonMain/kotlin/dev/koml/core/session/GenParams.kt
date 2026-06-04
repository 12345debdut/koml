package dev.koml.core.session

data class GenParams(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val stopSequences: List<String> = emptyList(),
    val seed: Long? = null,
)
