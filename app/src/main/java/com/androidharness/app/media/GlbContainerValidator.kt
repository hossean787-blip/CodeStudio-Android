package com.androidharness.app.media

/**
 * Strict header and chunk framing checks for user-supplied binary glTF 2.0 (.glb) files.
 * This does not prove that the mesh, animation, rig, textures, or external assets are valid.
 * Never treat this as a safe-to-render or safe-to-execute decision.
 */
object GlbContainerValidator {
    private const val MAX_BYTES = 128 * 1024 * 1024
    private const val JSON_CHUNK = 0x4E4F534A
    private const val BIN_CHUNK = 0x004E4942

    fun validate(bytes: ByteArray): Boolean {
        if (bytes.size < 20 || bytes.size > MAX_BYTES) return false
        fun uint32(offset: Int): Long {
            if (offset < 0 || offset + 4 > bytes.size) return -1
            return (0..3).fold(0L) { acc, index ->
                acc or ((bytes[offset + index].toLong() and 255L) shl (index * 8))
            }
        }
        if (uint32(0) != 0x46546C67L || uint32(4) != 2L || uint32(8) != bytes.size.toLong()) return false
        var offset = 12
        var chunks = 0
        while (offset < bytes.size) {
            if (bytes.size - offset < 8) return false
            val length = uint32(offset)
            val type = uint32(offset + 4)
            if (length < 0 || length % 4 != 0L || length > (bytes.size - offset - 8).toLong()) return false
            if (chunks == 0 && (type != JSON_CHUNK.toLong() || length == 0L)) return false
            if (chunks == 1 && type != BIN_CHUNK.toLong()) return false
            if (chunks >= 2) return false
            offset += 8 + length.toInt()
            chunks++
        }
        return chunks >= 1 && offset == bytes.size
    }
}
