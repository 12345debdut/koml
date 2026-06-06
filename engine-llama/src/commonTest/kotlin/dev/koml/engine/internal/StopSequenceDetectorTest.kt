package dev.koml.engine.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StopSequenceDetectorTest {

    @Test fun singlePieceContainingFullSequence_matches() {
        val det = StopSequenceDetector(listOf("<|im_end|>"))
        assertEquals("<|im_end|>", det.append("hello<|im_end|>"))
    }

    @Test fun sequenceSplitAcrossTwoPieces_matchesOnSecond() {
        val det = StopSequenceDetector(listOf("<|im_end|>"))
        assertNull(det.append("hello <|im_"))
        assertEquals("<|im_end|>", det.append("end|>"))
    }

    @Test fun sequenceSplitAcrossManyPieces_matchesOnFinal() {
        val det = StopSequenceDetector(listOf("<|eot_id|>"))
        assertNull(det.append("<"))
        assertNull(det.append("|"))
        assertNull(det.append("eot"))
        assertNull(det.append("_id"))
        assertEquals("<|eot_id|>", det.append("|>"))
    }

    @Test fun noMatch_returnsNull() {
        val det = StopSequenceDetector(listOf("<|im_end|>"))
        assertNull(det.append("the quick brown fox"))
    }

    @Test fun emptyStopSequence_isFilteredOut_doesNotMatchEveryToken() {
        // Bug in pre-v0.0.5 inline code: stopBuffer.endsWith("") == true,
        // so any piece would trip the stop. Detector now filters empties.
        val det = StopSequenceDetector(listOf("", "<|im_end|>"))
        assertNull(det.append("anything"))
        assertNull(det.append(" else"))
        assertEquals("<|im_end|>", det.append("<|im_end|>"))
    }

    @Test fun firstMatchingSequence_wins_inDeclarationOrder() {
        val det = StopSequenceDetector(listOf("ABC", "BC"))
        // Buffer ends with both ABC and BC; ABC is declared first.
        assertEquals("ABC", det.append("xxxABC"))
    }

    @Test fun bufferGrowsAtLeastTwiceLongestSequence_evenIfMaxBufferIsTiny() {
        // Pathological: stop sequence is 50 chars long, but maxBuffer = 4.
        // Inline pre-v0.0.5 code would keep only 4 chars and never match.
        val longStop = "X".repeat(50)
        val det = StopSequenceDetector(listOf(longStop), maxBuffer = 4)
        // Feed exactly the long sequence; should match on the final piece.
        assertNull(det.append(longStop.dropLast(1)))
        assertEquals(longStop, det.append("X"))
    }

    @Test fun bufferTrimsToMaintainSize_butKeepsTailIntactForSubsequentMatches() {
        val det = StopSequenceDetector(listOf("STOP"), maxBuffer = 8)
        // Pump enough pieces that we exceed the buffer cap.
        det.append("AAAA")
        det.append("BBBB")
        det.append("CCCC")
        // Buffer should be trimmed to at most 8 chars (the cap, given STOP is 4 long
        // → effectiveBuffer = max(8, 4*2) = 8). Verify a final STOP still matches.
        assertEquals("STOP", det.append("STOP"))
    }

    @Test fun emptySequenceList_neverMatches() {
        val det = StopSequenceDetector(emptyList())
        assertNull(det.append("anything"))
        assertNull(det.append("<|im_end|>"))
    }
}
