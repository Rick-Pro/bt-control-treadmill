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
    /** Heart rate in bpm from byte 13, or 0. The HY-BA06B1 has no sensor and always sends 0. */
    val heartRateBpm: Int = 0,
    /** Current incline from byte 11, or 0. This pad has no incline and sends 0. */
    val incline: Int = 0,
    /** Byte 24: changed from CC to CD when a walk started on Ricardo's pad. Logged, not used. */
    val sessionId: Int = 0,
    /** ASCII id of the pad (bytes 32 to 49 of the 52-byte frame, up to the first NUL), or null in the 47-byte frame. */
    val deviceId: String? = null,
    /**
     * An unknown u16 that rises with belt load and decays after STOP. It sits at offset 41 or 43 of the 47-byte frame
     * (both seen), so this is whichever of the two is not zero, or null when both are. Logged as "load", never shown.
     */
    val load: Int? = null,
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
