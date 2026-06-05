package dev.koml.core.config

/**
 * Per-load runtime parameters for [dev.koml.core.LlmCoordinator.loadModel].
 *
 * @property contextSize size of the model's KV cache in tokens; must be ≤
 *   the model's declared `contextWindow`. Bigger = more conversational
 *   history but more RAM and slower first-token latency.
 * @property threads number of CPU threads used by llama.cpp during decode.
 *   Default 4 is reasonable for phones and laptops; bump on desktops.
 * @property gpuLayers number of transformer layers to offload to the GPU
 *   (Metal on Apple, CUDA on Linux). `0` = pure CPU. iOS in v0.0.x is
 *   CPU-only — see `docs/known-issues.md#1`.
 */
data class RuntimeConfig(
    val contextSize: Int = 2048,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
)
