@file:OptIn(ExperimentalForeignApi::class)

package dev.koml.engine

import dev.koml.engine.native.LLAMA_DEFAULT_SEED
import dev.koml.engine.native.llama_backend_init
import dev.koml.engine.native.llama_batch_get_one
import dev.koml.engine.native.llama_context_default_params
import dev.koml.engine.native.llama_decode
import dev.koml.engine.native.llama_free
import dev.koml.engine.native.llama_init_from_model
import dev.koml.engine.native.llama_model_default_params
import dev.koml.engine.native.llama_model_free
import dev.koml.engine.native.llama_model_get_vocab
import dev.koml.engine.native.llama_model_load_from_file
import dev.koml.engine.native.llama_sampler_chain_add
import dev.koml.engine.native.llama_sampler_chain_default_params
import dev.koml.engine.native.llama_sampler_chain_init
import dev.koml.engine.native.llama_sampler_free
import dev.koml.engine.native.llama_sampler_init_dist
import dev.koml.engine.native.llama_sampler_init_temp
import dev.koml.engine.native.llama_sampler_init_top_k
import dev.koml.engine.native.llama_sampler_init_top_p
import dev.koml.engine.native.llama_sampler_sample
import dev.koml.engine.native.llama_token_to_piece
import dev.koml.engine.native.llama_tokenize
import dev.koml.engine.native.llama_vocab_is_eog
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.readValue
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents

internal actual class LlamaNative actual constructor() {

    actual fun initBackend() {
        llama_backend_init()
    }

    actual fun loadModel(path: String): Long {
        val model = llama_model_default_params().useContents {
            n_gpu_layers = 0
            llama_model_load_from_file(path, readValue())
        } ?: throw RuntimeException("Failed to load model from $path")
        return model.toLong()
    }

    actual fun freeModel(handle: Long) {
        handle.toCPointer<cnames.structs.llama_model>()?.let { llama_model_free(it) }
    }

    actual fun createContext(modelHandle: Long, contextSize: Int): Long {
        val model = modelHandle.toCPointer<cnames.structs.llama_model>()
            ?: throw RuntimeException("Invalid model handle")
        val ctx = llama_context_default_params().useContents {
            n_ctx = contextSize.toUInt()
            n_threads = 4
            llama_init_from_model(model, readValue())
        } ?: throw RuntimeException("Failed to create context")
        return ctx.toLong()
    }

    actual fun freeContext(ctxHandle: Long) {
        ctxHandle.toCPointer<cnames.structs.llama_context>()?.let { llama_free(it) }
    }

    actual fun tokenize(modelHandle: Long, text: String, addBos: Boolean): IntArray = memScoped {
        val model = modelHandle.toCPointer<cnames.structs.llama_model>()
            ?: throw RuntimeException("Invalid model handle")
        val vocab = llama_model_get_vocab(model)
            ?: throw RuntimeException("Failed to get vocab")
        val textLen = text.length

        val needed = -llama_tokenize(vocab, text, textLen, null, 0, addBos, false)
        if (needed <= 0) throw RuntimeException("Tokenization probe failed")

        val buf = allocArray<IntVar>(needed)
        val n = llama_tokenize(vocab, text, textLen, buf, needed, addBos, false)
        if (n < 0) throw RuntimeException("Tokenization failed")

        IntArray(n) { buf[it] }
    }

    actual fun decode(ctxHandle: Long, tokens: IntArray): Boolean {
        val ctx = ctxHandle.toCPointer<cnames.structs.llama_context>()
            ?: throw RuntimeException("Invalid context handle")
        return tokens.usePinned { pinned ->
            val batch = llama_batch_get_one(pinned.addressOf(0), tokens.size)
            llama_decode(ctx, batch) == 0
        }
    }

    actual fun sampleToken(ctxHandle: Long, temperature: Float, topP: Float, topK: Int): Int {
        val ctx = ctxHandle.toCPointer<cnames.structs.llama_context>()
            ?: throw RuntimeException("Invalid context handle")
        val sampler = llama_sampler_chain_init(llama_sampler_chain_default_params())
            ?: throw RuntimeException("Sampler chain init failed")
        try {
            llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK))
            llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1u))
            llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature))
            llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED.toUInt()))
            return llama_sampler_sample(sampler, ctx, -1)
        } finally {
            llama_sampler_free(sampler)
        }
    }

    actual fun tokenToPiece(modelHandle: Long, token: Int): String = memScoped {
        val model = modelHandle.toCPointer<cnames.structs.llama_model>()
            ?: throw RuntimeException("Invalid model handle")
        val vocab = llama_model_get_vocab(model)
            ?: throw RuntimeException("Failed to get vocab")

        val buf = allocArray<ByteVar>(256)
        val n = llama_token_to_piece(vocab, token, buf, 256, 0, true)
        if (n >= 0) return@memScoped buf.readBytes(n).decodeToString()

        val largeBuf = allocArray<ByteVar>(-n)
        val n2 = llama_token_to_piece(vocab, token, largeBuf, -n, 0, true)
        if (n2 <= 0) "" else largeBuf.readBytes(n2).decodeToString()
    }

    actual fun isEogToken(modelHandle: Long, token: Int): Boolean {
        val model = modelHandle.toCPointer<cnames.structs.llama_model>()
            ?: throw RuntimeException("Invalid model handle")
        val vocab = llama_model_get_vocab(model)
            ?: throw RuntimeException("Failed to get vocab")
        return llama_vocab_is_eog(vocab, token)
    }
}
