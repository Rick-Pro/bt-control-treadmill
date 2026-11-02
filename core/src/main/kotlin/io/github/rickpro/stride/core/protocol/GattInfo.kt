package io.github.rickpro.stride.core.protocol

import java.util.Locale
import java.util.UUID

/**
 * What a GATT server offers, as plain data. The app copies the Android service list into these types so that
 * transport detection and the diagnostics table live in core and are unit-tested without Android.
 */
data class GattCharacteristicInfo(val uuid: UUID, val properties: Int) {
    val canWrite: Boolean
        get() = properties and (GattProperties.WRITE or GattProperties.WRITE_NO_RESPONSE) != 0
    val canNotify: Boolean
        get() = properties and (GattProperties.NOTIFY or GattProperties.INDICATE) != 0
}

data class GattServiceInfo(val uuid: UUID, val characteristics: List<GattCharacteristicInfo>)

/** The bit values of `BluetoothGattCharacteristic.PROPERTY_*`, repeated because core has no Android imports. */
object GattProperties {
    const val BROADCAST = 0x01
    const val READ = 0x02
    const val WRITE_NO_RESPONSE = 0x04
    const val WRITE = 0x08
    const val NOTIFY = 0x10
    const val INDICATE = 0x20
    const val SIGNED_WRITE = 0x40
    const val EXTENDED = 0x80

    private val names = listOf(
        BROADCAST to "BROADCAST",
        READ to "READ",
        WRITE_NO_RESPONSE to "WRITE_NO_RESPONSE",
        WRITE to "WRITE",
        NOTIFY to "NOTIFY",
        INDICATE to "INDICATE",
        SIGNED_WRITE to "SIGNED_WRITE",
        EXTENDED to "EXTENDED",
    )

    fun names(properties: Int): List<String> = names.filter { properties and it.first != 0 }.map { it.second }
}

/** The GATT table as readable lines for the diagnostics log. */
object GattTable {
    private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /** "FBA0" for `0000fba0-0000-1000-8000-00805f9b34fb`, or null for a vendor UUID. */
    fun shortName(uuid: UUID): String? {
        val text = uuid.toString()
        return if (text.startsWith("0000") && text.endsWith(BASE_SUFFIX)) text.substring(4, 8).uppercase(Locale.ROOT) else null
    }

    fun label(uuid: UUID): String = shortName(uuid)?.let { "$uuid ($it)" } ?: uuid.toString()

    fun lines(services: List<GattServiceInfo>): List<String> {
        if (services.isEmpty()) return listOf("no GATT services")
        return buildList {
            for (service in services) {
                add("service ${label(service.uuid)}")
                if (service.characteristics.isEmpty()) add("    (no characteristics)")
                for (characteristic in service.characteristics) {
                    val names = GattProperties.names(characteristic.properties).joinToString(" ").ifEmpty { "none" }
                    add("    char ${label(characteristic.uuid)} props 0x%02X %s".format(Locale.ROOT, characteristic.properties, names))
                }
            }
        }
    }
}
