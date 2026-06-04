package dev.koml.registry

import dev.koml.core.model.ModelInfo
import dev.koml.core.model.ModelLicense
import dev.koml.core.model.PromptTemplate

/**
 * The five curated models that ship with v0.0.2. All are permissively
 * licensed (Apache-2.0 or MIT) and directly downloadable from the listed
 * HuggingFace URLs without any auth.
 *
 * **SHA-256s and exact byte sizes are placeholders.** Run
 * `scripts/refresh-manifest-shas.sh` before cutting a release to populate
 * them with the actual values for the current files on HuggingFace.
 *
 * License-acceptance is left wired but dormant (all five entries have
 * `requiresAcceptance = false`). When gated models (Llama, Gemma) are added
 * in Phase 3, flip the flag there and the existing `LicenseGate` code path
 * activates automatically.
 */
internal object CuratedModels {

    private val apache2 = ModelLicense(
        spdxId = "Apache-2.0",
        displayName = "Apache License 2.0",
        fullTextUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        termsUrl = null,
        requiresAcceptance = false,
    )

    private val mit = ModelLicense(
        spdxId = "MIT",
        displayName = "MIT License",
        fullTextUrl = "https://opensource.org/licenses/MIT",
        termsUrl = null,
        requiresAcceptance = false,
    )

    val list: List<ModelInfo> = listOf(
        ModelInfo(
            id = "smollm2-135m-instruct-q8",
            displayName = "SmolLM2-135M-Instruct (Q8_0)",
            sizeBytes = 145_500_672L, // TODO: refresh-manifest-shas.sh
            sha256 = "TODO_sha256_smollm2_135m_q8",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q8_0.gguf",
            promptTemplate = PromptTemplate.ChatML,
            contextWindow = 2048,
            license = apache2,
            recommendedRamMb = 256,
        ),
        ModelInfo(
            id = "smollm2-1.7b-instruct-q4km",
            displayName = "SmolLM2-1.7B-Instruct (Q4_K_M)",
            sizeBytes = 1_055_000_000L, // TODO
            sha256 = "TODO_sha256_smollm2_17b_q4km",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
            promptTemplate = PromptTemplate.ChatML,
            contextWindow = 2048,
            license = apache2,
            recommendedRamMb = 1_536,
        ),
        ModelInfo(
            id = "tinyllama-1.1b-chat-q4km",
            displayName = "TinyLlama-1.1B-Chat-v1.0 (Q4_K_M)",
            sizeBytes = 668_788_096L, // TODO
            sha256 = "TODO_sha256_tinyllama_q4km",
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            promptTemplate = PromptTemplate.ChatML,
            contextWindow = 2048,
            license = apache2,
            recommendedRamMb = 1_024,
        ),
        ModelInfo(
            id = "phi-3-mini-4k-instruct-q4",
            displayName = "Phi-3-mini-4k-instruct (Q4)",
            sizeBytes = 2_393_000_000L, // TODO
            sha256 = "TODO_sha256_phi3_mini_4k_q4",
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            promptTemplate = PromptTemplate.Phi3,
            contextWindow = 4096,
            license = mit,
            recommendedRamMb = 3_072,
        ),
        ModelInfo(
            id = "qwen2.5-1.5b-instruct-q4km",
            displayName = "Qwen2.5-1.5B-Instruct (Q4_K_M)",
            sizeBytes = 986_000_000L, // TODO
            sha256 = "TODO_sha256_qwen25_15b_q4km",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            promptTemplate = PromptTemplate.ChatML,
            contextWindow = 32_768,
            license = apache2,
            recommendedRamMb = 1_536,
        ),
    )
}
