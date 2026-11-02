package io.github.rickpro.stride.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogMaskTest {
    private fun hex(text: String) = text.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** The 56-byte wrapped notification with the pad's id, as captured. */
    private val withId = hex(
        "4D 04 01 34 68 34 00 00 00 00 00 00 00 00 09 00 00 00 00 00 00 00 00 00 00 00 00 00 CB 1B 82 17 70 00 05 00 " +
            "74 4F 68 33 31 36 67 4C 6F 35 42 4B 75 31 33 34 41 00 10 43",
    )

    @Test
    fun `the id of a wrapped 52 byte status is masked and nothing else is`() {
        val shown = LogMask.notificationHex(withId).split(" ")
        assertEquals(56, shown.size)
        assertEquals(18, shown.count { it == "**" })
        // header (4) + inner offsets 32 to 49
        assertTrue((36 until 54).all { shown[it] == "**" })
        assertEquals(withId.toHex().split(" ").take(36), shown.take(36))
        assertEquals(withId.toHex().split(" ").drop(54), shown.drop(54)) // the XOR and the trailer stay
        assertFalse(LogMask.notificationHex(withId).contains("74 4F 68"))
    }

    @Test
    fun `a bare 52 byte status is masked at the same inner offsets`() {
        val inner = PitPatWrapper.unwrap(withId)!!
        val shown = LogMask.notificationHex(inner).split(" ")
        assertEquals(52, shown.size)
        assertTrue((32 until 50).all { shown[it] == "**" })
        assertEquals(18, shown.count { it == "**" })
    }

    @Test
    fun `frames without an id are shown whole`() {
        val idle47 = hex(
            "4D 04 02 2F 68 2F 00 00 00 00 00 00 00 00 09 00 00 00 00 00 00 00 00 00 00 00 00 00 CB 1B 82 17 70 00 05 00 " +
                "00 00 00 00 00 00 00 00 00 00 00 00 00 16 43",
        )
        assertEquals(idle47.toHex(), LogMask.notificationHex(idle47))
        assertEquals(PitPatProtocol.keepAliveFrame.toHex(), LogMask.notificationHex(PitPatProtocol.keepAliveFrame))
        assertEquals("", LogMask.notificationHex(ByteArray(0)))
        assertEquals("4D", LogMask.notificationHex(byteArrayOf(0x4D)))
    }
}
