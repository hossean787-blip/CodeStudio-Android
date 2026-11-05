package com.androidharness.app.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlbContainerChunkBoundaryTest {
    private fun put(bytes: ByteArray, offset: Int, value: Int) {
        for (i in 0..3) bytes[offset + i] = (value ushr (i * 8)).toByte()
    }

    private fun glbWithBin(extraChunk: Boolean = false): ByteArray {
        val bytes = ByteArray(if (extraChunk) 44 else 36)
        put(bytes, 0, 0x46546C67)
        put(bytes, 4, 2)
        put(bytes, 8, bytes.size)
        put(bytes, 12, 4)
        put(bytes, 16, 0x4E4F534A)
        byteArrayOf(123, 125, 32, 32).copyInto(bytes, 20)
        put(bytes, 24, 4)
        put(bytes, 28, 0x004E4942)
        if (extraChunk) {
            put(bytes, 36, 0)
            put(bytes, 40, 0x004E4942)
        }
        return bytes
    }

    @Test fun acceptsValidBinaryChunk() =
        assertTrue(GlbContainerValidator.validate(glbWithBin()))

    @Test fun rejectsUnexpectedThirdChunk() =
        assertFalse(GlbContainerValidator.validate(glbWithBin(extraChunk = true)))

    @Test fun rejectsWrongSecondChunkType() {
        val bytes = glbWithBin()
        put(bytes, 28, 0x4E4F534A)
        assertFalse(GlbContainerValidator.validate(bytes))
    }

    @Test fun rejectsDeclaredLengthMismatch() {
        val bytes = glbWithBin()
        put(bytes, 8, bytes.size + 4)
        assertFalse(GlbContainerValidator.validate(bytes))
    }
}
