// Ported from Franrodr10/pitpat-controller (MIT), see THIRD_PARTY_NOTICES.md.
package io.github.rickpro.stride.core.protocol

import java.util.UUID

/**
 * Describes how to talk to one family of treadmills over Bluetooth Low Energy.
 *
 * The app only knows this interface. To support another treadmill, implement it and add the
 * implementation to the list of supported protocols in the app (see README).
 */
interface TreadmillProtocol {
    /** Name shown to the user, e.g. "PitPat". */
    val name: String

    /** GATT service that holds the command and status characteristics. */
    val service: UUID

    /** Characteristic that receives the command frames (written with response). */
    val commandCharacteristic: UUID

    /** Characteristics to subscribe to; notify or indicate is enabled, whichever they support. */
    val statusCharacteristics: List<UUID>

    /** Advertised service UUIDs and name prefixes that identify a compatible treadmill. */
    val advertisedServices: List<UUID>
    val namePrefixes: List<String>

    /** Speeds (km/h) the app may command. Nothing outside this range is ever sent. */
    val speedRange: ClosedFloatingPointRange<Double>

    /** Frame sent every [keepAliveIntervalMs] to keep the link alive, or null if there is none. */
    val keepAliveFrame: ByteArray?
    val keepAliveIntervalMs: Long

    fun startFrame(speedKmh: Double): ByteArray
    fun speedFrame(speedKmh: Double): ByteArray
    fun pauseFrame(): ByteArray
    fun stopFrame(): ByteArray

    /** Creates the decoder for one connection. Decoders may keep partial frames between calls. */
    fun newDecoder(): StatusDecoder

    fun matchesAdvertisement(name: String?, services: Collection<UUID>): Boolean =
        namePrefixes.any { name?.startsWith(it) == true } || services.any { it in advertisedServices }
}

fun interface StatusDecoder {
    /** Decodes the bytes received from [characteristic] at [timeMs] and returns every complete status. */
    fun decode(characteristic: UUID, data: ByteArray, timeMs: Long): List<TreadmillStatus>
}

enum class RunState { STOPPED, STARTING, RUNNING, PAUSED }

data class TreadmillStatus(
    val state: RunState,
    val speedKmh: Double,
    val targetSpeedKmh: Double,
    val distanceKm: Double,
    val durationSeconds: Long,
    /** Steps as reported by the treadmill; some firmwares always report 0. */
    val steps: Long,
    val kcal: Int,
    /** Maximum speed reported by the treadmill, or null if it does not report one. */
    val maxSpeedKmh: Double?,
    /** Firmware version byte of the status frame (27 on Ricardo's pad); 0 when unknown. */
    val firmware: Int = 0,
) {
    val isMoving: Boolean get() = state == RunState.RUNNING || state == RunState.STARTING

    /**
     * The pad reports STOPPED at once after STOP while the belt is still slowing down (1.9 km/h to 0 in about three
     * seconds on Ricardo's pad). That is "stopping", not stopped.
     */
    val isStopping: Boolean get() = state == RunState.STOPPED && speedKmh >= STOPPING_SPEED_KMH

    companion object {
        /** Below this the belt counts as stopped; the pad's own display shows 0.0 up to here. */
        const val STOPPING_SPEED_KMH = 0.1
    }
}

/** Expands a 16-bit UUID (e.g. 0xFBA0) with the Bluetooth base UUID. */
fun uuid16(short: Int): UUID = UUID.fromString("%08x-0000-1000-8000-00805f9b34fb".format(short))

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
