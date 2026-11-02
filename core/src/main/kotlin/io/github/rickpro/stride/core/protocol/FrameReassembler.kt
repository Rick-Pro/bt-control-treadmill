// Ported from Franrodr10/pitpat-controller (MIT), see THIRD_PARTY_NOTICES.md.
package io.github.rickpro.stride.core.protocol

/**
 * Rebuilds frames that arrive split across several notifications (small ATT MTU), using the
 * total-length byte at offset 1. The pieces of one frame arrive within milliseconds of each
 * other, so data left waiting for more than [gapMs] is dropped.
 */
class FrameReassembler(private val gapMs: Long = 300) {
    private var buffer = ByteArray(0)
    private var lastMs = 0L

    /** True while the start of a frame is waiting for its rest, so the next chunk continues it. */
    fun hasPending(timeMs: Long): Boolean = buffer.isNotEmpty() && timeMs - lastMs <= gapMs

    fun feed(chunk: ByteArray, timeMs: Long): List<ByteArray> {
        if (buffer.isNotEmpty() && timeMs - lastMs > gapMs) buffer = ByteArray(0)
        lastMs = timeMs
        buffer += chunk
        val frames = mutableListOf<ByteArray>()
        while (buffer.size >= 2) {
            val length = buffer[1].toInt() and 0xFF
            when {
                length < 5 -> { // not a length byte: pass the data through as it is
                    frames += buffer
                    buffer = ByteArray(0)
                }
                buffer.size >= length -> {
                    frames += buffer.copyOfRange(0, length)
                    buffer = buffer.copyOfRange(length, buffer.size)
                }
                else -> break
            }
        }
        return frames
    }
}
