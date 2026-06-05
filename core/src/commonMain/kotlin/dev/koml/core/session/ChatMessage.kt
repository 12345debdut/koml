package dev.koml.core.session

/**
 * One turn in a chat conversation. Pass a list of these to
 * [dev.koml.core.LlmSession.chat] to drive multi-turn generation.
 *
 * @property role who's speaking — drives template formatting.
 * @property content the actual text content. No special tokens needed
 *   here; the chat template adds them.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
)

/**
 * Speaker role for [ChatMessage]. Note that not every model supports
 * every role — Gemma has no system role, for example; the relevant chat
 * template silently merges system content into the first user message.
 */
enum class ChatRole {
    System,
    User,
    Assistant,
}
