package io.github.rickpro.stride.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Notifications captured from Ricardo's pad (PitPat-T01, HY-BA06B1) over the wrapped transport, FF01 / FF02.
 * The header is `4D <direction> <counter> <length>`: direction 04 from the pad (Stride sends 00), length is the
 * size of the inner status frame, which starts at byte 4.
 */
class PadCapturesTest {
    /** 56 bytes: header, then a 52-byte status frame that carries an 18-byte ASCII device id at 32 to 49. */
    private val long = hex(
        "4D 04 01 34 68 34 00 00 00 00 00 00 00 00 09 00 00 00 00 00 00 00 00 00 00 00 00 00 CB 1B 82 17 70 00 05 00 " +
            "74 4F 68 33 31 36 67 4C 6F 35 42 4B 75 31 33 34 41 00 10 43",
    )

    /** 51 bytes: header, then a 47-byte status frame without the device id. */
    private val short = hex(
        "4D 04 02 2F 68 2F 00 00 00 00 00 00 00 00 09 00 00 00 00 00 00 00 00 00 00 00 00 00 CB 1B 82 17 70 00 05 00 " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 16 43",
    )

    private fun hex(text: String) = text.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun wrapped() = PitPatLink(PitPatVariant.WRAPPED)

    private fun assertIsTheIdleStatus(status: TreadmillStatus) {
        assertEquals(RunState.STOPPED, status.state)
        assertEquals(0.0, status.speedKmh, 1e-9)
        assertEquals(0.0, status.targetSpeedKmh, 1e-9)
        assertEquals(6.0, status.maxSpeedKmh!!, 1e-9)
        assertEquals(0.009, status.distanceKm, 1e-9)
        assertEquals(27, status.firmware)
        assertEquals(0L, status.steps)
        assertEquals(0, status.kcal)
        assertEquals(0L, status.durationSeconds)
    }

    @Test
    fun `the captures have the sizes the header says`() {
        assertEquals(56, long.size)
        assertEquals(4 + (long[3].toInt() and 0xFF), long.size)
        assertEquals(51, short.size)
        assertEquals(4 + (short[3].toInt() and 0xFF), short.size)
        assertEquals(0x04, long[1].toInt())
        assertEquals(0x04, short[1].toInt())
    }

    @Test
    fun `the 52 byte capture decodes to an idle pad`() {
        assertIsTheIdleStatus(wrapped().decode(long, 0).single())
    }

    @Test
    fun `the 47 byte capture decodes to an idle pad`() {
        assertIsTheIdleStatus(wrapped().decode(short, 0).single())
    }

    @Test
    fun `both captures decode one after the other on one link`() {
        val link = wrapped()
        assertIsTheIdleStatus(link.decode(long, 0).single())
        assertIsTheIdleStatus(link.decode(short, 1_000).single())
        assertEquals(PitPatLink.SpeedScale.THOUSANDTHS, link.speedScale) // 6000 raw is a normal thousandths max
    }

    @Test
    fun `the inner frame of each capture is a valid PitPat status frame`() {
        for (capture in listOf(long, short)) {
            val inner = PitPatWrapper.unwrap(capture)!!
            assertEquals(capture.size - 4, inner.size)
            assertEquals(0x68, inner[0].toInt())
            assertEquals(inner.size, inner[1].toInt() and 0xFF)
            assertTrue(PitPatProtocol.isValidFrame(inner))
            assertEquals(inner.last(), 0x43.toByte())
        }
    }

    @Test
    fun `a capture split over several notifications still decodes`() {
        val link = wrapped()
        assertTrue(link.decode(long.copyOfRange(0, 20), 0).isEmpty())
        assertTrue(link.decode(long.copyOfRange(20, 44), 5).isEmpty())
        assertIsTheIdleStatus(link.decode(long.copyOfRange(44, long.size), 10).single())
    }

    @Test
    fun `any direction byte is accepted and the length comes from byte 3`() {
        for (direction in listOf(0x00, 0x04, 0x01, 0xFF)) {
            val changed = long.copyOf().also { it[1] = direction.toByte() }
            assertIsTheIdleStatus(wrapped().decode(changed, 0).single())
        }
        // Bytes after the length the header announces are not part of the frame.
        assertIsTheIdleStatus(wrapped().decode(short + byteArrayOf(0x00, 0x7F), 0).single())
        // A frame that is not a status (here the 5-byte heartbeat) is ignored, not an error.
        assertTrue(wrapped().decode(hex("4D 04 03 05 6A 05 FD F8 43"), 0).isEmpty())
    }

    @Test
    fun `a frame with a bad checksum or trailer is rejected`() {
        val badChecksum = short.copyOf().also { it[short.size - 2] = (it[short.size - 2] + 1).toByte() }
        val badTrailer = short.copyOf().also { it[short.size - 1] = 0x44 }
        assertTrue(wrapped().decode(badChecksum, 0).isEmpty())
        assertTrue(wrapped().decode(badTrailer, 0).isEmpty())
    }

    @Test
    fun `our own writes keep their own counter and the 00 direction`() {
        val link = wrapped()
        // The pad's counters (01, 02) are the pad's own: they do not move ours.
        link.decode(long, 0)
        val first = link.encode(PitPatProtocol.keepAliveFrame)
        link.decode(short, 100)
        val second = link.encode(PitPatProtocol.keepAliveFrame)
        assertArrayEquals(hex("4D 00 00 05 6A 05 FD F8 43"), first)
        assertArrayEquals(hex("4D 00 01 05 6A 05 FD F8 43"), second)
    }

    @Test
    fun `our counter wraps from 255 to 0 even with the pad talking in between`() {
        val link = wrapped()
        var last = -1
        for (i in 0 until 600) {
            if (i % 3 == 0) link.decode(if (i % 2 == 0) long else short, i * 10L)
            val counter = link.encode(PitPatProtocol.keepAliveFrame)[2].toInt() and 0xFF
            assertEquals(i % 256, counter)
            if (last == 255) assertEquals(0, counter)
            last = counter
        }
    }
}
