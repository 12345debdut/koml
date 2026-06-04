package dev.koml.engine

internal actual class LlamaNative actual constructor() {

    actual external fun initBackend()
    actual external fun loadModel(path: String): Long
    actual external fun freeModel(handle: Long)
    actual external fun createContext(modelHandle: Long, contextSize: Int): Long
    actual external fun freeContext(ctxHandle: Long)
    actual external fun tokenize(modelHandle: Long, text: String, addBos: Boolean): IntArray
    actual external fun decode(ctxHandle: Long, tokens: IntArray): Boolean
    actual external fun sampleToken(ctxHandle: Long, temperature: Float, topP: Float, topK: Int): Int
    actual external fun tokenToPiece(modelHandle: Long, token: Int): String
    actual external fun isEogToken(modelHandle: Long, token: Int): Boolean

    companion object {
        init {
            System.loadLibrary("koml-jni")
        }
    }
}
