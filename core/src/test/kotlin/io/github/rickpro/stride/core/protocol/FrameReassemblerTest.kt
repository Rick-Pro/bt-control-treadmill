package io.github.rickpro.stride.core.protocol

import org.junit.Test
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class FrameReassemblerTest {
    private val frame = TestFrames.status()

    @Test
    fun `complete frames pass straight through`() {
        val out = FrameReassembler().feed(frame, 0)
        assertEquals(1, out.size)
        assertArrayEquals(frame, out[0])
    }

    @Test
    fun `joins a frame split in pieces and keeps two frames in one chunk apart`() {
        val reassembler = FrameReassembler()
        assertTrue(reassembler.feed(frame.copyOfRange(0, 20), 0).isEmpty())
        assertTrue(reassembler.feed(frame.copyOfRange(20, 40), 5).isEmpty())
        assertArrayEquals(frame, reassembler.feed(frame.copyOfRange(40, 52), 10).single())
        assertEquals(2, reassembler.feed(frame + frame, 20).size)
    }

    @Test
    fun `drops a partial frame that is never completed`() {
        val reassembler = FrameReassembler(gapMs = 300)
        reassembler.feed(frame.copyOfRange(0, 20), 0)
        val out = reassembler.feed(frame, 1_000)
        assertArrayEquals(frame, out.single())
    }

    @Test
    fun `says when a frame is waiting for its rest`() {
        val reassembler = FrameReassembler(gapMs = 300)
        assertFalse(reassembler.hasPending(0))
        reassembler.feed(frame.copyOfRange(0, 20), 100)
        assertTrue(reassembler.hasPending(150))
        assertFalse(reassembler.hasPending(1_000)) // older than the gap: it would be dropped
        reassembler.feed(frame.copyOfRange(20, 52), 200)
        assertFalse(reassembler.hasPending(210))
    }

    @Test
    fun `passes through data without a length byte`() {
        val odd = byteArrayOf(0x01, 0x00, 0x7F)
        assertArrayEquals(odd, FrameReassembler().feed(odd, 0).single())
    }
}
