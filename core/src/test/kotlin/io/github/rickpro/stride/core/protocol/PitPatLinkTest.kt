package io.github.rickpro.stride.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitPatLinkTest {
    private val logged = mutableListOf<String>()
    private fun wrapped() = PitPatLink(PitPatVariant.WRAPPED) { logged += it }
    private fun hex(text: String) = text.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // ------------------------------------------------------------------ wrapper

    @Test
    fun `start at 1 km per hour with counter 2 is the documented wrapped frame`() {
        val expected = hex("4D 00 02 17 6A 17 00 00 00 00 03 E8 01 00 50 00 04 00 00 00 0D BA 9D 76 EF 1A 43")
        assertArrayEquals(expected, PitPatWrapper.wrap(2, PitPatProtocol.startFrame(1.0)))

        // The link counts writes: two heartbeats first, so the start is the third write and carries counter 2.
        val link = wrapped()
        link.encode(PitPatProtocol.keepAliveFrame)
        link.encode(PitPatProtocol.keepAliveFrame)
        assertArrayEquals(expected, link.encode(PitPatProtocol.startFrame(1.0)))
    }

    @Test
    fun `heartbeat is 4D 00 counter 05 followed by the keep-alive frame`() {
        val link = wrapped()
        assertArrayEquals(hex("4D 00 00 05 6A 05 FD F8 43"), link.encode(PitPatProtocol.keepAliveFrame))
        assertArrayEquals(hex("4D 00 01 05 6A 05 FD F8 43"), link.encode(PitPatProtocol.keepAliveFrame))
    }

    @Test
    fun `counter goes up by one per write and wraps after 255`() {
        val link = wrapped()
        val counters = List(258) { link.encode(PitPatProtocol.keepAliveFrame)[2].toInt() and 0xFF }
        assertEquals((0..255).toList() + listOf(0, 1), counters)
    }

    @Test
    fun `the length byte is the size of the frame that follows`() {
        val link = wrapped()
        val stop = link.encode(PitPatProtocol.stopFrame())
        assertEquals(0x17, stop[3].toInt())
        assertEquals(4 + 23, stop.size)
        assertArrayEquals(PitPatProtocol.stopFrame(), PitPatWrapper.unwrap(stop))
    }

    @Test
    fun `unwrap leaves data without the header alone`() {
        assertNull(PitPatWrapper.unwrap(TestFrames.status()))
        assertNull(PitPatWrapper.unwrap(byteArrayOf(0x4D, 0x00, 0x01, 0x00)))
        assertNull(PitPatWrapper.unwrap(byteArrayOf(0x4D, 0x01, 0x01, 0x05, 0x6A)))
    }

    @Test
    fun `classic and plain variants send frames exactly as built`() {
        for (variant in listOf(PitPatVariant.CLASSIC, PitPatVariant.PLAIN_FFF)) {
            val link = PitPatLink(variant)
            assertArrayEquals(PitPatProtocol.startFrame(2.0), link.encode(PitPatProtocol.startFrame(2.0)))
            assertArrayEquals(PitPatProtocol.keepAliveFrame, link.encode(PitPatProtocol.keepAliveFrame))
        }
        assertFalse(PitPatLink(PitPatVariant.CLASSIC).heartbeatPerNotification)
        assertFalse(PitPatLink(PitPatVariant.PLAIN_FFF).heartbeatPerNotification)
        assertTrue(wrapped().heartbeatPerNotification)
    }

    // ------------------------------------------------------------------ decoding

    @Test
    fun `a wrapped status is parsed after the header is stripped`() {
        val status = TestFrames.status(speed = 2500, target = 2500)
        val parsed = wrapped().decode(PitPatWrapper.wrap(7, status), 0).single()
        assertEquals(2.5, parsed.speedKmh, 1e-9)
        assertEquals(RunState.RUNNING, parsed.state)
        assertEquals(PitPatProtocol.parseStatus(status), parsed)
    }

    @Test
    fun `a wrapped status split over several notifications is joined`() {
        val link = wrapped()
        val wire = PitPatWrapper.wrap(3, TestFrames.status(speed = 3000))
        assertTrue(link.decode(wire.copyOfRange(0, 20), 0).isEmpty())
        assertTrue(link.decode(wire.copyOfRange(20, 40), 5).isEmpty())
        assertEquals(3.0, link.decode(wire.copyOfRange(40, wire.size), 10).single().speedKmh, 1e-9)
    }

    @Test
    fun `a continuation chunk that happens to start with 4D 00 is not taken for a header`() {
        // The duration field (payload offsets 20 to 23) is 4D 00 2E E0, and the notification is split exactly there.
        val status = TestFrames.status(speed = 3000, durationRaw = 0x4D00_0000L + 12_000)
        assertEquals(0x4D, status[20].toInt())
        assertEquals(0x00, status[21].toInt())
        val wire = PitPatWrapper.wrap(3, status)
        val link = wrapped()
        assertTrue(link.decode(wire.copyOfRange(0, 24), 0).isEmpty()) // header + payload bytes 0 to 19
        val parsed = link.decode(wire.copyOfRange(24, wire.size), 5).single() // payload bytes 20 to 51
        assertEquals(3.0, parsed.speedKmh, 1e-9)
        assertEquals((0x4D00_0000L + 12_000) / 1000, parsed.durationSeconds)
    }

    @Test
    fun `short acknowledgements and stray bytes are ignored`() {
        val link = wrapped()
        assertTrue(link.decode(byteArrayOf(0x4D, 0x00, 0x05, 0x01, 0x00), 0).isEmpty())
        assertTrue(link.decode(byteArrayOf(0x01), 10).isEmpty())
        // and they do not poison the next real status
        assertEquals(1, link.decode(PitPatWrapper.wrap(1, TestFrames.status()), 20).size)
    }

    @Test
    fun `classic decoding is the same as the PitPat decoder`() {
        val frame = TestFrames.status(speed = 1800, target = 2000)
        val reference = PitPatProtocol.newDecoder()
        val link = PitPatLink(PitPatVariant.CLASSIC)
        for ((i, chunk) in listOf(frame.copyOfRange(0, 20), frame.copyOfRange(20, 52), frame).withIndex()) {
            val t = i * 10L
            assertEquals(reference.decode(PitPatProtocol.statusCharacteristics.first(), chunk, t), link.decode(chunk, t))
        }
    }

    @Test
    fun `plain FFF variant reads bare frames`() {
        val parsed = PitPatLink(PitPatVariant.PLAIN_FFF).decode(TestFrames.status(speed = 1500), 0).single()
        assertEquals(1.5, parsed.speedKmh, 1e-9)
    }

    // ------------------------------------------------------------------ speed scale

    private fun statusFrom(speed: Int, target: Int, state: Int = 0x08, max: Int = 6000) =
        TestFrames.status(flags = state, speed = speed, target = target, maxSpeed = max)

    @Test
    fun `thousandths are confirmed when the pad echoes the commanded target`() {
        val link = wrapped()
        link.encode(PitPatProtocol.startFrame(1.0))
        val status = link.decode(PitPatWrapper.wrap(0, statusFrom(0, 1000, state = 0x18)), 0).single()
        assertEquals(1.0, status.targetSpeedKmh, 1e-9)
        assertEquals(PitPatLink.SpeedScale.THOUSANDTHS, link.speedScale)
        assertTrue(logged.single().startsWith("speed scale: thousandths"))
    }

    @Test
    fun `an echo ten times too small switches to hundredths and is read again`() {
        val link = wrapped()
        link.encode(PitPatProtocol.startFrame(2.0))
        // The pad reports 2.0 km/h as 200: read as thousandths that is 0.2, a tenth of the commanded 2.0.
        // No max speed in this status (0 = not reported), so only the echo can tell.
        val status = link.decode(PitPatWrapper.wrap(0, statusFrom(speed = 0, target = 200, max = 0)), 0).single()
        assertEquals(PitPatLink.SpeedScale.HUNDREDTHS, link.speedScale)
        assertEquals(2.0, status.targetSpeedKmh, 1e-9)
        assertTrue(logged.single().startsWith("speed scale: hundredths"))
        assertTrue(logged.single().contains("echoed"))
    }

    @Test
    fun `a max speed below the minimum speed switches before any command is sent`() {
        val link = wrapped()
        val status = link.decode(PitPatWrapper.wrap(0, statusFrom(speed = 0, target = 0, state = 0x00, max = 600)), 0).single()
        assertEquals(PitPatLink.SpeedScale.HUNDREDTHS, link.speedScale)
        assertEquals(6.0, status.maxSpeedKmh!!, 1e-9)
        assertTrue(logged.single().startsWith("speed scale: hundredths"))
    }

    @Test
    fun `a normal max speed does not switch`() {
        val link = wrapped()
        link.decode(PitPatWrapper.wrap(0, statusFrom(speed = 0, target = 0, state = 0x00, max = 6000)), 0)
        assertEquals(PitPatLink.SpeedScale.THOUSANDTHS, link.speedScale)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `after the switch commands are sent in hundredths with a valid checksum`() {
        val link = wrapped()
        link.decode(PitPatWrapper.wrap(0, statusFrom(speed = 0, target = 0, state = 0x00, max = 600)), 0)
        val sent = PitPatWrapper.unwrap(link.encode(PitPatProtocol.startFrame(2.0)))!!
        assertEquals(0x00, sent[6].toInt() and 0xFF)
        assertEquals(0xC8, sent[7].toInt() and 0xFF) // 200 = 2.0 km/h in hundredths
        assertTrue(PitPatProtocol.isValidFrame(sent))
        val speed = PitPatWrapper.unwrap(link.encode(PitPatProtocol.speedFrame(3.5)))!!
        assertEquals(350, ((speed[6].toInt() and 0xFF) shl 8) or (speed[7].toInt() and 0xFF))
        assertTrue(PitPatProtocol.isValidFrame(speed))
    }

    @Test
    fun `the 1 to 6 km per hour clamp holds in both scales`() {
        fun raw(frame: ByteArray) = ((frame[6].toInt() and 0xFF) shl 8) or (frame[7].toInt() and 0xFF)
        val thousandths = wrapped()
        assertEquals(6000, raw(PitPatWrapper.unwrap(thousandths.encode(PitPatProtocol.startFrame(9.0)))!!))
        assertEquals(1000, raw(PitPatWrapper.unwrap(thousandths.encode(PitPatProtocol.speedFrame(0.2)))!!))
        val hundredths = wrapped()
        hundredths.decode(PitPatWrapper.wrap(0, statusFrom(0, 0, state = 0x00, max = 600)), 0)
        assertEquals(600, raw(PitPatWrapper.unwrap(hundredths.encode(PitPatProtocol.startFrame(9.0)))!!))
        assertEquals(100, raw(PitPatWrapper.unwrap(hundredths.encode(PitPatProtocol.speedFrame(0.2)))!!))
    }

    @Test
    fun `stop pause and heartbeat frames are never rescaled`() {
        val link = wrapped()
        link.decode(PitPatWrapper.wrap(0, statusFrom(0, 0, state = 0x00, max = 600)), 0)
        for (frame in listOf(PitPatProtocol.stopFrame(), PitPatProtocol.pauseFrame(), PitPatProtocol.keepAliveFrame)) {
            assertArrayEquals(frame, PitPatWrapper.unwrap(link.encode(frame)))
        }
        val beeper = PitPatProtocol.beeperFrame(false)
        assertArrayEquals(beeper, PitPatWrapper.unwrap(link.encode(beeper)))
    }

    @Test
    fun `a stale or different echo does not switch and a later match confirms`() {
        val link = wrapped()
        link.encode(PitPatProtocol.startFrame(2.4))
        // The pad starts at 1.0 and ignores the start speed: 1.0 against 2.4 is not a tenth, so nothing switches.
        link.decode(PitPatWrapper.wrap(0, statusFrom(1000, 1000, state = 0x18)), 0)
        assertEquals(PitPatLink.SpeedScale.THOUSANDTHS, link.speedScale)
        assertTrue(logged.isEmpty())
        link.encode(PitPatProtocol.speedFrame(2.4))
        link.decode(PitPatWrapper.wrap(1, statusFrom(1500, 2400)), 20)
        assertEquals(PitPatLink.SpeedScale.THOUSANDTHS, link.speedScale)
        assertTrue(logged.single().startsWith("speed scale: thousandths"))
    }

    @Test
    fun `an echo that never matches is reported once and thousandths stay`() {
        val link = wrapped()
        link.encode(PitPatProtocol.startFrame(5.0))
        repeat(25) { i -> link.decode(PitPatWrapper.wrap(i, statusFrom(2000, 2000)), i * 100L) }
        assertEquals(PitPatLink.SpeedScale.THOUSANDTHS, link.speedScale)
        assertEquals(1, logged.size)
        assertTrue(logged.single().contains("not confirmed"))
    }

    @Test
    fun `a thousandths pad at any legal speed is never switched by the echo`() {
        for (commanded in 10..60) {
            val link = wrapped()
            val kmh = commanded / 10.0
            link.encode(PitPatProtocol.startFrame(kmh))
            // Any legal echo from 1.0 to 6.0 against any legal command stays clear of the tenth band.
            for (echo in listOf(1000, 2000, 3000, 6000, commanded * 100)) {
                link.decode(PitPatWrapper.wrap(0, statusFrom(echo, echo)), 0)
            }
            assertEquals("commanded $kmh", PitPatLink.SpeedScale.THOUSANDTHS, link.speedScale)
        }
    }

    @Test
    fun `classic pads never have their scale questioned`() {
        val link = PitPatLink(PitPatVariant.CLASSIC) { logged += it }
        link.encode(PitPatProtocol.startFrame(2.0))
        val status = link.decode(statusFrom(0, 200, max = 600), 0).single()
        assertEquals(0.2, status.targetSpeedKmh, 1e-9)
        assertEquals(PitPatLink.SpeedScale.THOUSANDTHS, link.speedScale)
        assertTrue(logged.isEmpty())
        assertNotNull(status.maxSpeedKmh)
    }
}
