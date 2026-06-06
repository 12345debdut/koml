package dev.koml.engine.internal

/**
 * Streaming detector for stop sequences. Tokens arrive piece by piece (the
 * model's tokeniser splits text into pieces that don't necessarily align with
 * the sequences we're matching against); this class buffers enough recent
 * output that any stop sequence — including ones that span multiple tokens —
 * gets caught on the iteration that completes it.
 *
 * Three subtleties the inline implementation used to get wrong:
 *
 * - **Empty stop sequences** match every iteration (`endsWith("") == true`).
 *   We drop them at construction time so callers can naïvely pass through
 *   user input.
 * - **Buffer smaller than the longest sequence** means a long stop sequence
 *   could never match. We grow the effective buffer to at least 2× the
 *   longest sequence, regardless of the supplied [maxBuffer].
 * - **Trimming before the match check** would erase the prefix of an
 *   in-progress sequence. We append → check → trim in that order so the
 *   match against the current iteration's full suffix always succeeds.
 */
internal class StopSequenceDetector(
    sequences: List<String>,
    maxBuffer: Int = 256,
) {
    private val seqs: List<String> = sequences.filter { it.isNotEmpty() }
    private val effectiveBuffer: Int = maxOf(maxBuffer, (seqs.maxOfOrNull { it.length } ?: 0) * 2)
    private val buf = StringBuilder()

    /**
     * Appends [piece] to the rolling buffer and returns the first stop
     * sequence whose suffix matches the buffer's tail (or `null`).
     */
    fun append(piece: String): String? {
        buf.append(piece)
        val match = seqs.firstOrNull { buf.endsWith(it) }
        if (buf.length > effectiveBuffer) {
            buf.deleteRange(0, buf.length - effectiveBuffer)
        }
        return match
    }

    /** For tests / debugging. */
    internal val bufferedTail: String get() = buf.toString()
}
