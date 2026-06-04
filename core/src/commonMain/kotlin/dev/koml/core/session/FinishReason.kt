package dev.koml.core.session

enum class FinishReason {
    EndOfText,
    MaxTokensReached,
    StopSequence,
    Cancelled,
}
