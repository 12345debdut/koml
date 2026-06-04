package dev.koml.core.config

data class RuntimeConfig(
    val contextSize: Int = 2048,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
)
