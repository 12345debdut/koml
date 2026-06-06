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
            sizeBytes = 144811360L,
            sha256 = "5a1395716f7913741cc51d98581b9b1228d80987a9f7d3664106742eb06bba83",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q8_0.gguf",
            promptTemplate = PromptTemplate.ChatML,
            contextWindow = 2048,
            license = apache2,
            recommendedRamMb = 256,
        ),
        ModelInfo(
            id = "smollm2-1.7b-instruct-q4km",
            displayName = "SmolLM2-1.7B-Instruct (Q4_K_M)",
            sizeBytes = 1055609824L,
            sha256 = "77665ea4815999596525c636fbeb56ba8b080b46ae85efef4f0d986a139834d7",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
            promptTemplate = PromptTemplate.ChatML,
            contextWindow = 2048,
            license = apache2,
            recommendedRamMb = 1_536,
        ),
        ModelInfo(
            id = "tinyllama-1.1b-chat-q4km",
            displayName = "TinyLlama-1.1B-Chat-v1.0 (Q4_K_M)",
            sizeBytes = 668788096L,
            sha256 = "9fecc3b3cd76bba89d504f29b616eedf7da85b96540e490ca5824d3f7d2776a0",
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            promptTemplate = PromptTemplate.ChatML,
            contextWindow = 2048,
            license = apache2,
            recommendedRamMb = 1_024,
        ),
        ModelInfo(
            id = "phi-3-mini-4k-instruct-q4",
            displayName = "Phi-3-mini-4k-instruct (Q4)",
            sizeBytes = 2393231072L,
            sha256 = "8a83c7fb9049a9b2e92266fa7ad04933bb53aa1e85136b7b30f1b8000ff2edef",
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            promptTemplate = PromptTemplate.Phi3,
            contextWindow = 4096,
            license = mit,
            recommendedRamMb = 3_072,
        ),
        ModelInfo(
            id = "qwen2.5-1.5b-instruct-q4km",
            displayName = "Qwen2.5-1.5B-Instruct (Q4_K_M)",
            sizeBytes = 986048768L,
            sha256 = "1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            promptTemplate = PromptTemplate.ChatML,
            contextWindow = 32_768,
            license = apache2,
            recommendedRamMb = 1_536,
        ),
    )
}
