package com.androidharness.app.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlbContainerValidatorTest {
    private fun glb(json: ByteArray = byteArrayOf(123, 125, 32, 32)): ByteArray {
        val size = 20 + json.size
        val bytes = ByteArray(size)
        fun put(offset: Int, value: Int) {
            for (i in 0..3) bytes[offset + i] = (value ushr (i * 8)).toByte()
        }
        put(0, 0x46546C67)
        put(4, 2)
        put(8, size)
        put(12, json.size)
        put(16, 0x4E4F534A)
        json.copyInto(bytes, 20)
        return bytes
    }

    @Test fun acceptsMinimalFramedGlb() = assertTrue(GlbContainerValidator.validate(glb()))
    @Test fun rejectsTruncatedAndEmpty() {
        assertFalse(GlbContainerValidator.validate(byteArrayOf()))
        assertFalse(GlbContainerValidator.validate(glb().copyOf(19)))
    }
    @Test fun rejectsInvalidMagicVersionAndLength() {
        for (offset in listOf(0, 4, 8)) {
            val data = glb()
            data[offset] = 0
            assertFalse(GlbContainerValidator.validate(data))
        }
    }
    @Test fun rejectsUnalignedChunkAndOversizedChunk() {
        val a = glb()
        a[12] = 3
        assertFalse(GlbContainerValidator.validate(a))
        val b = glb()
        b[12] = 127
        assertFalse(GlbContainerValidator.validate(b))
    }
    @Test fun rejectsWrongFirstChunkType() {
        val a = glb()
        a[16] = 0
        assertFalse(GlbContainerValidator.validate(a))
    }
}
