package dev.koml.download

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256UtilTest {
    @Test fun hexLowerEncodesByteArray() {
        // bytes [0x00, 0x10, 0xff] -> "0010ff"
        assertEquals("0010ff", byteArrayOf(0, 0x10, 0xFF.toByte()).toHexLower())
    }

    @Test fun emptyArrayProducesEmptyString() {
        assertEquals("", ByteArray(0).toHexLower())
    }
}
