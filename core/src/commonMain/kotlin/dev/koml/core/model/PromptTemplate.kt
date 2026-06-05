package dev.koml.core.model

/**
 * The chat template family a model expects. Drives how
 * [dev.koml.core.LlmSession.chat] formats [dev.koml.core.session.ChatMessage]s
 * before tokenisation.
 *
 * Each value corresponds to a concrete renderer in
 * `dev.koml.engine.chat.ChatTemplate`. Pick from the model's
 * `tokenizer_config.json` `chat_template` field on Hugging Face when
 * authoring [ModelInfo] entries.
 */
enum class PromptTemplate {
    /** No special formatting — messages are concatenated with newlines. */
    None,

    /** ChatML: `<|im_start|>{role}\n...<|im_end|>`. SmolLM2, Qwen2.5, etc. */
    ChatML,

    /** Llama 3 / 3.1 / 3.2: `<|begin_of_text|>`, `<|start_header_id|>`, `<|eot_id|>`. */
    Llama3,

    /** Phi-3 / 3.5: `<|system|>`, `<|user|>`, `<|assistant|>`, `<|end|>`. */
    Phi3,

    /** Gemma / Gemma 2: `<start_of_turn>{role}\n...<end_of_turn>` (no system role). */
    Gemma,
}
