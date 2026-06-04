package dev.koml.core.session

data class ChatMessage(
    val role: ChatRole,
    val content: String,
)

enum class ChatRole {
    System,
    User,
    Assistant,
}
