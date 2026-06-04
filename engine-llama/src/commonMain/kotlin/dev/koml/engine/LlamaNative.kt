package dev.koml.engine

internal expect class LlamaNative() {
    fun initBackend()
    fun loadModel(path: String): Long
    fun freeModel(handle: Long)
    fun createContext(modelHandle: Long, contextSize: Int): Long
    fun freeContext(ctxHandle: Long)
    fun tokenize(modelHandle: Long, text: String, addBos: Boolean): IntArray
    fun decode(ctxHandle: Long, tokens: IntArray): Boolean
    fun sampleToken(ctxHandle: Long, temperature: Float, topP: Float, topK: Int): Int
    fun tokenToPiece(modelHandle: Long, token: Int): String
    fun isEogToken(modelHandle: Long, token: Int): Boolean
}
