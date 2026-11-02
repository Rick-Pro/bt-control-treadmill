package io.github.rickpro.stride.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            "45 58 41 4D 50 4C 45 53 45 52 49 41 4C 30 30 30 31 00 44 43",
    )

    /** 51 bytes: header, then a 47-byte status frame without the device id. */
    private val short = hex(
        "4D 04 02 2F 68 2F 00 00 00 00 00 00 00 00 09 00 00 00 00 00 00 00 00 00 00 00 00 00 CB 1B 82 17 70 00 05 00 " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 16 43",
    )

    /**
     * Build 3 log, belt about to move: the pad beeps and counts down. Flags 9A (80 imperial panel, 18 starting, 02 always
     * set), session id CC, and an unknown u16 ("load") of 01 58 at inner offset 43 to 44.
     */
    private val starting = hex(
        "4D 04 02 2F 68 2F 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 CC 1B 9A 17 70 00 05 00 " +
        "00 00 00 00 00 00 00 00 00 00 00 01 58 59 43",
    )

    /**
     * Build 3 log, two seconds into a walk: speed 1.60, target 2.40, 1 m, 2000 ms. Flags 8A (running), session id CD,
     * and the same kind of unknown u16 ("load") of 01 96, this time at inner offset 41 to 42.
     */
    private val running = hex(
        "4D 04 0A 2F 68 2F 00 06 40 09 60 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 07 D0 CD 1B 8A 17 70 00 05 00 " +
        "00 00 00 00 00 00 00 00 00 01 96 00 00 7F 43",
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

    @Test
    fun `the STARTING capture decodes to the countdown`() {
        val status = wrapped().decode(starting, 0).single()
        assertEquals(RunState.STARTING, status.state)
        assertTrue(status.isMoving) // the app treats the countdown as moving: STOP must work during it
        assertEquals(0.0, status.speedKmh, 1e-9)
        assertEquals(0.0, status.targetSpeedKmh, 1e-9)
        assertEquals(0.0, status.distanceKm, 1e-9) // the leftover counter from the last walk was reset on start
        assertEquals(0L, status.durationSeconds)
        assertEquals(6.0, status.maxSpeedKmh!!, 1e-9)
        assertEquals(27, status.firmware)
    }

    @Test
    fun `the RUNNING capture decodes to a walk two seconds in`() {
        val status = wrapped().decode(running, 0).single()
        assertEquals(RunState.RUNNING, status.state)
        assertEquals(1.60, status.speedKmh, 1e-9)
        assertEquals(2.40, status.targetSpeedKmh, 1e-9)
        assertEquals(0.001, status.distanceKm, 1e-9) // distance is in metres: 1 m
        assertEquals(2L, status.durationSeconds) // duration is in milliseconds: 2000 ms
        assertEquals(0L, status.steps) // this pad never fills in steps or calories
        assertEquals(0, status.kcal)
        assertEquals(6.0, status.maxSpeedKmh!!, 1e-9)
        assertEquals(27, status.firmware)
    }

    @Test
    fun `the three captured walk frames follow on one link as a countdown then a walk`() {
        val link = wrapped()
        assertEquals(RunState.STOPPED, link.decode(long, 0).single().state) // idle, flags 82
        assertEquals(RunState.STARTING, link.decode(starting, 100).single().state) // flags 9A
        assertEquals(RunState.RUNNING, link.decode(running, 3_200).single().state) // flags 8A
    }

    @Test
    fun `flags 82 9A and 8A are stopped starting and running, whatever the unknown bits say`() {
        fun state(flags: Int) = PitPatProtocol.parseStatus(TestFrames.status(flags = flags))!!.state
        assertEquals(RunState.STOPPED, state(0x82))
        assertEquals(RunState.STARTING, state(0x9A))
        assertEquals(RunState.RUNNING, state(0x8A))
        // 80 is the imperial panel and 01 is Wi-Fi: neither changes the run state. 02 is set in every capture.
        assertEquals(RunState.STOPPED, state(0x03))
        assertEquals(RunState.RUNNING, state(0x09))
        assertEquals(RunState.PAUSED, state(0x92))
    }

    @Test
    fun `the new captures are valid frames with a matching length and trailer`() {
        for (capture in listOf(starting, running)) {
            assertEquals(51, capture.size)
            assertEquals(47, capture[3].toInt() and 0xFF)
            val inner = PitPatWrapper.unwrap(capture)!!
            assertEquals(47, inner.size)
            assertTrue(PitPatProtocol.isValidFrame(inner))
        }
    }

    // ------------------------------------------------------------------ what else the frames carry

    @Test
    fun `the 52 byte capture carries the pad's id and the 47 byte ones do not`() {
        val withId = wrapped().decode(long, 0).single()
        // The id is the printable ASCII in inner bytes 32 to 49 up to the NUL; read it from the bytes themselves.
        val inner = PitPatWrapper.unwrap(long)!!
        val expected = String(inner, 32, 18, Charsets.US_ASCII).substringBefore('\u0000')
        assertEquals(expected, withId.deviceId)
        assertEquals(17, withId.deviceId!!.length)
        assertTrue(withId.deviceId!!.all { it.isLetterOrDigit() })
        assertNull(wrapped().decode(short, 0).single().deviceId)
        assertNull(wrapped().decode(starting, 0).single().deviceId)
        assertNull(wrapped().decode(running, 0).single().deviceId)
    }

    @Test
    fun `the session id changes when a walk starts`() {
        val link = wrapped()
        assertEquals(0xCB, link.decode(short, 0).single().sessionId) // idle, before the walk
        assertEquals(0xCC, link.decode(starting, 100).single().sessionId)
        assertEquals(0xCD, link.decode(running, 3_200).single().sessionId)
    }

    @Test
    fun `the unknown load value is read from whichever offset holds it`() {
        val link = wrapped()
        assertNull(link.decode(short, 0).single().load) // idle: both offsets are zero
        assertEquals(0x158, link.decode(starting, 100).single().load) // 01 58 at inner offset 43
        assertEquals(0x196, link.decode(running, 3_200).single().load) // 01 96 at inner offset 41
        assertNull(link.decode(long, 4_000).single().load) // the 52-byte frame has the id there, not a load
    }

    @Test
    fun `this pad reports no heart rate, incline, steps or calories`() {
        for (capture in listOf(long, short, starting, running)) {
            val status = wrapped().decode(capture, 0).single()
            assertEquals(0, status.heartRateBpm)
            assertEquals(0, status.incline)
            assertEquals(0L, status.steps)
            assertEquals(0, status.kcal)
        }
    }

    @Test
    fun `heart rate and incline are read when a pad does send them`() {
        val status = PitPatProtocol.parseStatus(TestFrames.status(heartRate = 72, incline = 3))!!
        assertEquals(72, status.heartRateBpm)
        assertEquals(3, status.incline)
        val none = PitPatProtocol.parseStatus(TestFrames.status())!!
        assertEquals(0, none.heartRateBpm)
        assertEquals(0, none.incline)
    }

    @Test
    fun `an id that is not text, or too short, is not an id`() {
        assertEquals("ABC-1234", PitPatProtocol.parseStatus(TestFrames.status(deviceId = "ABC-1234"))!!.deviceId)
        assertNull(PitPatProtocol.parseStatus(TestFrames.status())!!.deviceId) // all NUL
        assertNull(PitPatProtocol.parseStatus(TestFrames.status(deviceId = "AB"))!!.deviceId) // under 3 characters
        val binary = TestFrames.status().also {
            it[32] = 0x01
            it[33] = 0x02
            it[34] = 0x03
            it[50] = PitPatProtocol.checksum(it)
        }
        assertNull(PitPatProtocol.parseStatus(binary)!!.deviceId)
    }
}
