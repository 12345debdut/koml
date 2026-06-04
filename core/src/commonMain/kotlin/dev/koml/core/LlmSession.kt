package dev.koml.core

import dev.koml.core.model.ModelInfo
import dev.koml.core.session.ChatMessage
import dev.koml.core.session.GenParams
import dev.koml.core.session.TokenEvent
import kotlinx.coroutines.flow.Flow

interface LlmSession {
    val model: ModelInfo
    val contextWindow: Int

    fun generate(prompt: String, params: GenParams = GenParams()): Flow<TokenEvent>

    suspend fun complete(prompt: String, params: GenParams = GenParams()): String

    suspend fun chat(messages: List<ChatMessage>, params: GenParams = GenParams()): Flow<TokenEvent>

    suspend fun tokenCount(text: String): Int

    suspend fun unload()
}
