package dev.koml.engine.internal

import dev.koml.core.LlmSession
import dev.koml.core.config.RuntimeConfig
import dev.koml.core.error.KomlException
import dev.koml.core.model.ModelHandle
import dev.koml.core.model.ModelInfo
import dev.koml.core.session.ChatMessage
import dev.koml.core.session.FinishReason
import dev.koml.core.session.GenParams
import dev.koml.core.session.GenStats
import dev.koml.core.session.TokenEvent
import dev.koml.engine.LlamaNative
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

internal class DefaultLlmSession(
    private val native: LlamaNative,
    private val handle: ModelHandle,
    private val runtime: RuntimeConfig,
    private val modelPtr: Long,
    private val contextPtr: Long,
    private val sessionDispatcher: CoroutineDispatcher,
    private val onUnload: suspend (LlmSession) -> Unit,
) : LlmSession {

    override val model: ModelInfo = handle.info
    override val contextWindow: Int = runtime.contextSize

    private val stateMutex = Mutex()
    private var unloaded = false

    override fun generate(prompt: String, params: GenParams): Flow<TokenEvent> = flow {
        if (unloaded) {
            emit(TokenEvent.Error(KomlException.InferenceException(
                "Session for model '${model.id}' was already unloaded; obtain a fresh one via coordinator.loadModel(...)"
            )))
            return@flow
        }

        val promptMark = TimeSource.Monotonic.markNow()

        val promptTokens = try {
            native.tokenize(modelPtr, prompt, addBos = true)
        } catch (e: Throwable) {
            emit(TokenEvent.Error(KomlException.InferenceException(
                "Tokenization failed for model '${model.id}': ${e.message}", e,
            )))
            return@flow
        }

        if (!native.decode(contextPtr, promptTokens)) {
            emit(TokenEvent.Error(KomlException.InferenceException(
                "Decode failed for prompt (${promptTokens.size} tokens) on model '${model.id}' — " +
                    "context window ($contextWindow) may be exceeded.",
            )))
            return@flow
        }

        val promptEvalMs = promptMark.elapsedNow().inWholeMilliseconds
        val generateMark = TimeSource.Monotonic.markNow()

        val stopBuffer = StringBuilder()
        var generated = 0
        var finishReason = FinishReason.MaxTokensReached

        for (i in 0 until params.maxTokens) {
            currentCoroutineContext().ensureActive()

            val token = native.sampleToken(contextPtr, params.temperature, params.topP, params.topK)

            if (native.isEogToken(modelPtr, token)) {
                finishReason = FinishReason.EndOfText
                break
            }

            val piece = native.tokenToPiece(modelPtr, token)
            stopBuffer.append(piece)

            val matchedStop = params.stopSequences.firstOrNull { stopBuffer.endsWith(it) }
            if (matchedStop != null) {
                finishReason = FinishReason.StopSequence
                break
            }

            emit(TokenEvent.Token(piece, token))
            generated++

            if (!native.decode(contextPtr, intArrayOf(token))) {
                emit(TokenEvent.Error(KomlException.InferenceException(
                    "Decode failed at generated token #$generated on model '${model.id}' — " +
                        "context window ($contextWindow) likely exceeded.",
                )))
                return@flow
            }

            if (stopBuffer.length > MAX_STOP_BUFFER) {
                stopBuffer.deleteRange(0, stopBuffer.length - MAX_STOP_BUFFER)
            }
        }

        val generateMs = generateMark.elapsedNow().inWholeMilliseconds
        val tps = if (generateMs > 0) generated * 1000.0 / generateMs else 0.0

        emit(
            TokenEvent.Done(
                reason = finishReason,
                stats = GenStats(
                    promptTokens = promptTokens.size,
                    generatedTokens = generated,
                    promptEvalMs = promptEvalMs,
                    generateMs = generateMs,
                    tokensPerSecond = tps,
                ),
            ),
        )
    }.flowOn(sessionDispatcher)

    override suspend fun complete(prompt: String, params: GenParams): String {
        val sb = StringBuilder()
        generate(prompt, params).collect { event ->
            when (event) {
                is TokenEvent.Token -> sb.append(event.text)
                is TokenEvent.Error -> throw event.cause
                is TokenEvent.Done -> Unit
            }
        }
        return sb.toString()
    }

    override suspend fun chat(messages: List<ChatMessage>, params: GenParams): Flow<TokenEvent> {
        val template = dev.koml.engine.chat.ChatTemplate.forPromptTemplate(model.promptTemplate)
        val prompt = template.render(messages)
        // Merge template's default stop sequences with caller-supplied ones so
        // the assistant turn terminates cleanly without forcing every caller
        // to remember each model's special tokens.
        val effectiveParams = if (template.defaultStopSequences.isEmpty()) {
            params
        } else {
            params.copy(stopSequences = (params.stopSequences + template.defaultStopSequences).distinct())
        }
        return generate(prompt, effectiveParams)
    }

    override suspend fun tokenCount(text: String): Int = withContext(sessionDispatcher) {
        native.tokenize(modelPtr, text, addBos = false).size
    }

    override suspend fun unload() {
        stateMutex.withLock {
            if (unloaded) return
            unloaded = true
        }
        withContext(sessionDispatcher) {
            native.freeContext(contextPtr)
            native.freeModel(modelPtr)
        }
        onUnload(this)
    }

    companion object {
        private const val MAX_STOP_BUFFER = 256
    }
}
