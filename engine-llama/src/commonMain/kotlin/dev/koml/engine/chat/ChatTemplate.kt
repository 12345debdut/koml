package dev.koml.engine.chat

import dev.koml.core.model.PromptTemplate
import dev.koml.core.session.ChatMessage
import dev.koml.core.session.ChatRole

/**
 * Renders a [List]&lt;[ChatMessage]&gt; into the model-specific prompt string
 * expected by each family's tokenizer. Every model family has its own special
 * tokens and turn structure — getting any of these wrong typically produces
 * subtly broken output (model rambles, ignores the system prompt, or never
 * stops generating).
 *
 * Sources: each template was derived from the model's official
 * `tokenizer_config.json` `chat_template` field on Hugging Face.
 *
 * Stop sequences are intentionally not appended here — the engine treats them
 * as a [dev.koml.core.session.GenParams.stopSequences] concern, which lets
 * callers override per-call without rebuilding the template logic.
 */
internal sealed class ChatTemplate {

    /**
     * Renders [messages] into the full prompt string fed to `generate()`.
     * The trailing assistant-turn opener (e.g. `<|im_start|>assistant\n`)
     * is included so the model writes directly into its own turn.
     */
    abstract fun render(messages: List<ChatMessage>): String

    /**
     * Stop sequences specific to this template. Callers may pass these into
     * [GenParams.stopSequences] to terminate cleanly at the end of the
     * assistant turn instead of running to `maxTokens`.
     */
    abstract val defaultStopSequences: List<String>

    companion object {
        /** Picks the right template for the [info]'s declared [PromptTemplate]. */
        fun forPromptTemplate(template: PromptTemplate): ChatTemplate = when (template) {
            PromptTemplate.None -> NoneTemplate
            PromptTemplate.ChatML -> ChatMLTemplate
            PromptTemplate.Llama3 -> Llama3Template
            PromptTemplate.Phi3 -> Phi3Template
            PromptTemplate.Gemma -> GemmaTemplate
        }
    }
}

/**
 * Pass-through: concatenate message content with newlines, no special tokens.
 * Used for base (non-instruct) models or when the caller wants to do their
 * own formatting.
 */
internal data object NoneTemplate : ChatTemplate() {
    override val defaultStopSequences: List<String> = emptyList()
    override fun render(messages: List<ChatMessage>): String =
        messages.joinToString(separator = "\n") { it.content }
}

/**
 * ChatML — used by SmolLM2, Qwen2.5, and many others. Each turn is wrapped
 * in `<|im_start|>{role}\n...<|im_end|>`. The assistant turn opener is left
 * unclosed so the model continues directly.
 */
internal data object ChatMLTemplate : ChatTemplate() {
    override val defaultStopSequences: List<String> = listOf("<|im_end|>")
    override fun render(messages: List<ChatMessage>): String = buildString {
        for (m in messages) {
            append("<|im_start|>")
            append(roleName(m.role))
            append('\n')
            append(m.content)
            append("<|im_end|>\n")
        }
        append("<|im_start|>assistant\n")
    }

    private fun roleName(role: ChatRole): String = when (role) {
        ChatRole.System -> "system"
        ChatRole.User -> "user"
        ChatRole.Assistant -> "assistant"
    }
}

/**
 * Llama 3 / 3.1 / 3.2 — uses `<|begin_of_text|>` once, then per-turn
 * `<|start_header_id|>{role}<|end_header_id|>\n\n{content}<|eot_id|>`.
 */
internal data object Llama3Template : ChatTemplate() {
    override val defaultStopSequences: List<String> = listOf("<|eot_id|>")
    override fun render(messages: List<ChatMessage>): String = buildString {
        append("<|begin_of_text|>")
        for (m in messages) {
            append("<|start_header_id|>")
            append(roleName(m.role))
            append("<|end_header_id|>\n\n")
            append(m.content)
            append("<|eot_id|>")
        }
        append("<|start_header_id|>assistant<|end_header_id|>\n\n")
    }

    private fun roleName(role: ChatRole): String = when (role) {
        ChatRole.System -> "system"
        ChatRole.User -> "user"
        ChatRole.Assistant -> "assistant"
    }
}

/**
 * Phi-3 / Phi-3.5 — `<|system|>`, `<|user|>`, `<|assistant|>` tags with
 * `<|end|>` between turns.
 */
internal data object Phi3Template : ChatTemplate() {
    override val defaultStopSequences: List<String> = listOf("<|end|>", "<|endoftext|>")
    override fun render(messages: List<ChatMessage>): String = buildString {
        for (m in messages) {
            append('<').append('|').append(roleName(m.role)).append('|').append('>').append('\n')
            append(m.content)
            append("<|end|>\n")
        }
        append("<|assistant|>\n")
    }

    private fun roleName(role: ChatRole): String = when (role) {
        ChatRole.System -> "system"
        ChatRole.User -> "user"
        ChatRole.Assistant -> "assistant"
    }
}

/**
 * Gemma / Gemma 2 — `<start_of_turn>{role}\n{content}<end_of_turn>` per
 * turn. Gemma does **not** have a system role; system content is prepended
 * to the first user message to preserve intent.
 */
internal data object GemmaTemplate : ChatTemplate() {
    override val defaultStopSequences: List<String> = listOf("<end_of_turn>")
    override fun render(messages: List<ChatMessage>): String {
        // Merge system content into the first user message since Gemma lacks a system role.
        val collapsed = mutableListOf<ChatMessage>()
        var pendingSystem: String? = null
        for (m in messages) {
            when (m.role) {
                ChatRole.System -> pendingSystem = (pendingSystem?.plus("\n\n") ?: "") + m.content
                ChatRole.User -> {
                    val merged = pendingSystem?.let { "$it\n\n${m.content}" } ?: m.content
                    collapsed.add(ChatMessage(ChatRole.User, merged))
                    pendingSystem = null
                }
                ChatRole.Assistant -> collapsed.add(m)
            }
        }
        // If only system messages were provided (unusual), surface them as a user turn.
        if (collapsed.isEmpty() && pendingSystem != null) {
            collapsed.add(ChatMessage(ChatRole.User, pendingSystem!!))
        }

        return buildString {
            for (m in collapsed) {
                append("<start_of_turn>")
                append(roleName(m.role))
                append('\n')
                append(m.content)
                append("<end_of_turn>\n")
            }
            append("<start_of_turn>model\n")
        }
    }

    private fun roleName(role: ChatRole): String = when (role) {
        ChatRole.User -> "user"
        ChatRole.Assistant -> "model"
        // System collapsed earlier; here for exhaustiveness only.
        ChatRole.System -> "user"
    }
}
