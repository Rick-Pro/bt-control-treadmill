package io.github.rickpro.stride.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PitPatTransportsTest {
    private val write = GattProperties.WRITE
    private val writeNoResponse = GattProperties.WRITE_NO_RESPONSE
    private val notify = GattProperties.NOTIFY
    private val read = GattProperties.READ

    private fun char(short: Int, props: Int) = GattCharacteristicInfo(uuid16(short), props)
    private fun service(short: Int, vararg chars: GattCharacteristicInfo) = GattServiceInfo(uuid16(short), chars.toList())

    private val deviceInfo = service(0x180A, char(0x2A29, read))
    private val classic = service(0xFBA0, char(0xFBA1, write), char(0xFBA2, notify))

    @Test
    fun `finds the FBA0 service as before`() {
        val found = PitPatTransports.detect(listOf(deviceInfo, classic))!!
        assertEquals(PitPatVariant.CLASSIC, found.variant)
        assertEquals(uuid16(0xFBA0), found.serviceUuid)
        assertEquals(uuid16(0xFBA1), found.writeUuid)
        assertEquals(uuid16(0xFBA2), found.notifyUuid)
    }

    @Test
    fun `FBA0 wins when the pad offers more than one pair`() {
        val both = service(0xFFFF, char(0xFF01, write), char(0xFF02, notify))
        assertEquals(PitPatVariant.CLASSIC, PitPatTransports.detect(listOf(both, classic))!!.variant)
    }

    @Test
    fun `FF01 and FF02 are found in whatever service holds them`() {
        for (holder in listOf(0xFFFF, 0xFF00, 0x1234)) {
            val services = listOf(deviceInfo, service(holder, char(0xFF01, write or writeNoResponse), char(0xFF02, notify)))
            val found = PitPatTransports.detect(services)!!
            assertEquals(PitPatVariant.WRAPPED, found.variant)
            assertEquals(uuid16(holder), found.serviceUuid)
            assertEquals(uuid16(0xFF01), found.writeUuid)
            assertEquals(uuid16(0xFF02), found.notifyUuid)
        }
    }

    @Test
    fun `FF01 and FF02 win over FFF1 and FFF2`() {
        val wrapped = service(0xFFFF, char(0xFF01, write), char(0xFF02, notify))
        val plain = service(0xFFF0, char(0xFFF1, write), char(0xFFF2, notify))
        assertEquals(PitPatVariant.WRAPPED, PitPatTransports.detect(listOf(plain, wrapped))!!.variant)
    }

    @Test
    fun `FFF1 and FFF2 are the last choice`() {
        val found = PitPatTransports.detect(listOf(deviceInfo, service(0xFFF0, char(0xFFF1, write), char(0xFFF2, notify))))!!
        assertEquals(PitPatVariant.PLAIN_FFF, found.variant)
        assertEquals(uuid16(0xFFF1), found.writeUuid)
        assertEquals(uuid16(0xFFF2), found.notifyUuid)
    }

    @Test
    fun `roles follow the properties when the pair is the other way round`() {
        val flipped = service(0xFFF0, char(0xFFF1, notify), char(0xFFF2, writeNoResponse))
        val found = PitPatTransports.detect(listOf(flipped))!!
        assertEquals(PitPatVariant.PLAIN_FFF, found.variant)
        assertEquals(uuid16(0xFFF2), found.writeUuid)
        assertEquals(uuid16(0xFFF1), found.notifyUuid)
        assertEquals(writeNoResponse, found.writeProperties)
    }

    @Test
    fun `a pair whose properties fit neither way is skipped`() {
        val readOnly = service(0xFFFF, char(0xFF01, read), char(0xFF02, read))
        assertNull(PitPatTransports.detect(listOf(readOnly)))
        // and detection goes on to the next variant instead of giving up
        val plain = service(0xFFF0, char(0xFFF1, write), char(0xFFF2, notify))
        assertEquals(PitPatVariant.PLAIN_FFF, PitPatTransports.detect(listOf(readOnly, plain))!!.variant)
    }

    @Test
    fun `both characteristics must be in the same service`() {
        val split = listOf(service(0xFFF0, char(0xFFF1, write)), service(0xFFF5, char(0xFFF2, notify)))
        assertNull(PitPatTransports.detect(split))
    }

    @Test
    fun `an unrelated device is not recognised`() {
        assertNull(PitPatTransports.detect(listOf(deviceInfo, service(0x1800, char(0x2A00, read)))))
        assertNull(PitPatTransports.detect(emptyList()))
    }

    @Test
    fun `a service with the right UUID but missing characteristics is not enough for FBA0`() {
        assertNull(PitPatTransports.detect(listOf(service(0xFBA0, char(0xFBA1, write)))))
    }

    @Test
    fun `the description names the variant, service and both characteristics`() {
        val text = PitPatTransports.detect(listOf(service(0xFFFF, char(0xFF01, write), char(0xFF02, notify))))!!.describe()
        assertTrue(text, text.startsWith("PitPat wrapped:"))
        assertTrue(text, "0000ffff-0000-1000-8000-00805f9b34fb (FFFF)" in text)
        assertTrue(text, "write 0000ff01-0000-1000-8000-00805f9b34fb (FF01)" in text)
        assertTrue(text, "notify 0000ff02-0000-1000-8000-00805f9b34fb (FF02)" in text)
    }

    @Test
    fun `variant names are the ones shown to the user`() {
        assertEquals(listOf("PitPat", "PitPat wrapped", "PitPat FFF"), PitPatVariant.entries.map { it.displayName })
        assertNotNull(PitPatVariant.entries.single { it.wrapped })
    }

    // ------------------------------------------------------------------ the table written to the diagnostics log

    @Test
    fun `the table lists every service and characteristic with its properties`() {
        val lines = GattTable.lines(
            listOf(
                deviceInfo,
                service(0xFFFF, char(0xFF01, write or writeNoResponse), char(0xFF02, notify or read)),
                GattServiceInfo(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"), listOf(GattCharacteristicInfo(UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"), 0))),
            ),
        )
        assertEquals(
            listOf(
                "service 0000180a-0000-1000-8000-00805f9b34fb (180A)",
                "    char 00002a29-0000-1000-8000-00805f9b34fb (2A29) props 0x02 READ",
                "service 0000ffff-0000-1000-8000-00805f9b34fb (FFFF)",
                "    char 0000ff01-0000-1000-8000-00805f9b34fb (FF01) props 0x0C WRITE_NO_RESPONSE WRITE",
                "    char 0000ff02-0000-1000-8000-00805f9b34fb (FF02) props 0x12 READ NOTIFY",
                "service 6e400001-b5a3-f393-e0a9-e50e24dcca9e",
                "    char 6e400002-b5a3-f393-e0a9-e50e24dcca9e props 0x00 none",
            ),
            lines,
        )
    }

    @Test
    fun `an empty table and a service without characteristics are still written`() {
        assertEquals(listOf("no GATT services"), GattTable.lines(emptyList()))
        assertTrue("(no characteristics)" in GattTable.lines(listOf(GattServiceInfo(uuid16(0x1801), emptyList())))[1])
    }

    @Test
    fun `short names only exist for the Bluetooth base UUID`() {
        assertEquals("FBA0", GattTable.shortName(uuid16(0xFBA0)))
        assertNull(GattTable.shortName(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")))
        assertNull(GattTable.shortName(UUID.fromString("0000fba0-0000-1000-8000-00805f9b34fc")))
    }
}
