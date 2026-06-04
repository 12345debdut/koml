package dev.koml.core.session

import dev.koml.core.error.KomlException

sealed class TokenEvent {
    data class Token(val text: String, val tokenId: Int) : TokenEvent()

    data class Done(val reason: FinishReason, val stats: GenStats) : TokenEvent()

    data class Error(val cause: KomlException) : TokenEvent()
}
