package io.github.rickpro.stride.core.protocol

import java.util.Locale

/**
 * The 4-byte header some PitPat pads put in front of every frame, in both directions:
 * `4D <direction> <counter> <length>`, then the normal frame.
 * - direction: `00` on what Stride writes, `04` on what the pad notifies (seen in captures from a HY-BA06B1).
 *   Stride accepts any value when reading.
 * - counter: one byte. Stride's own goes up by one with every write (heartbeats included) and wraps after 255.
 *   The pad counts its notifications separately; the two are never compared.
 * - length: the size of the frame that follows (52 or 47 for the two status frames captured).
 *
 * Protocol facts from azmke/pitpat-treadmill-control (MIT), see THIRD_PARTY_NOTICES.md and docs/protocol.md.
 */
object PitPatWrapper {
    const val MAGIC = 0x4D
    const val HEADER_SIZE = 4

    /** Second header byte on writes from Stride to the pad. */
    const val DIRECTION_TO_PAD = 0x00

    /** Second header byte on notifications from the pad. */
    const val DIRECTION_FROM_PAD = 0x04

    fun wrap(counter: Int, frame: ByteArray): ByteArray {
        require(frame.size <= 0xFF) { "a frame is at most 255 bytes" }
        val out = ByteArray(HEADER_SIZE + frame.size)
        out[0] = MAGIC.toByte()
        out[1] = DIRECTION_TO_PAD.toByte()
        out[2] = counter.toByte()
        out[3] = frame.size.toByte()
        frame.copyInto(out, HEADER_SIZE)
        return out
    }

    /**
     * The frame inside a wrapped notification: bytes 4 up to 4 + the length in byte 3, or fewer if the notification
     * is the first piece of a split frame. Null if [data] does not start with the magic byte `4D`. The direction
     * and counter bytes are not looked at.
     */
    fun unwrap(data: ByteArray): ByteArray? {
        if (data.size <= HEADER_SIZE || data[0] != MAGIC.toByte()) return null
        val length = data[3].toInt() and 0xFF
        return data.copyOfRange(HEADER_SIZE, minOf(data.size, HEADER_SIZE + length))
    }
}

/**
 * One connection's view of a PitPat pad: turns the normal frames from [PitPatProtocol] into what goes on the wire
 * for the detected [variant], and turns notifications back into statuses. It owns the write counter and the speed
 * scale, so it is created fresh for every connection and used from a single thread.
 *
 * - [encode]: the frame as it goes into one GATT write. For a wrapped pad that adds the header and counter.
 * - [decode]: the statuses in one notification. For a wrapped pad that strips the header first.
 *
 * Speed scale: the documented scale is thousandths of km/h (1.0 km/h is 1000). Some pads are reported to use
 * hundredths. On variants other than [PitPatVariant.CLASSIC] the link checks this and switches to hundredths when
 * the evidence says the pad does, and logs "speed scale: hundredths":
 * - the pad's reported maximum speed, read as thousandths, is below the 1.0 km/h minimum (checked on every status,
 *   so it normally switches before the first command is sent); or
 * - after a start or speed command, the target the pad echoes is about a tenth of what was commanded. For a pad that
 *   really uses thousandths this cannot happen: speeds are 1.0 to 6.0 km/h, so the echo is never below 1/6 of it.
 * It cannot see the case where the pad reads our thousandths as hundredths and echoes the same raw value back.
 * The clamp to the protocol speed range is applied by [PitPatProtocol] before a frame gets here and is not changed.
 */
class PitPatLink(val variant: PitPatVariant, private val log: (String) -> Unit = {}) {

    enum class SpeedScale(val unitsPerKmh: Int, val label: String) {
        THOUSANDTHS(PitPatProtocol.SPEED_UNITS_PER_KMH, "thousandths"),
        HUNDREDTHS(100, "hundredths"),
    }

    var speedScale = SpeedScale.THOUSANDTHS
        private set

    /** True when the pad needs a heartbeat after every notification instead of the timer. */
    val heartbeatPerNotification: Boolean get() = variant.heartbeatPerNotification

    private val reassembler = FrameReassembler()
    private var counter = 0
    private var verifying = variant.verifiesSpeedScale
    private var commandedKmh: Double? = null
    private var unconfirmed = 0

    /** The bytes for one GATT write. Call it exactly once per write: the counter moves on every call. */
    fun encode(frame: ByteArray): ByteArray {
        noteCommand(frame)
        val scaled = rescale(frame)
        return if (variant.wrapped) PitPatWrapper.wrap(nextCounter(), scaled) else scaled
    }

    /** Every complete status in one notification. Notifications that are not statuses give an empty list. */
    fun decode(data: ByteArray, timeMs: Long): List<TreadmillStatus> {
        val bytes = if (variant.wrapped && !reassembler.hasPending(timeMs)) {
            // A wrapped notification starts with the header. A shorter payload cannot be a status frame.
            val inner = PitPatWrapper.unwrap(data) ?: data
            if (inner.size < MIN_FRAME_SIZE) return emptyList()
            inner
        } else {
            data
        }
        val statuses = ArrayList<TreadmillStatus>(1)
        for (frame in reassembler.feed(bytes, timeMs)) {
            var status = PitPatProtocol.parseStatus(frame, speedScale.unitsPerKmh) ?: continue
            if (verifying && checkScale(status)) {
                status = PitPatProtocol.parseStatus(frame, speedScale.unitsPerKmh) ?: continue
            }
            statuses += status
        }
        return statuses
    }

    private fun nextCounter(): Int {
        val current = counter
        counter = (counter + 1) and 0xFF
        return current
    }

    // ------------------------------------------------------------------ speed scale

    private fun isSpeedCommand(frame: ByteArray) =
        frame.size == CONTROL_FRAME_SIZE && frame[0] == START_BYTE && (frame[12].toInt() and 0xFF) == OP_START

    private fun rawSpeed(frame: ByteArray) = ((frame[6].toInt() and 0xFF) shl 8) or (frame[7].toInt() and 0xFF)

    /** Remembers the speed of a start or speed command (always given in thousandths) to compare with the echo. */
    private fun noteCommand(frame: ByteArray) {
        if (!verifying || !isSpeedCommand(frame)) return
        val raw = rawSpeed(frame)
        if (raw == 0) return
        commandedKmh = raw / PitPatProtocol.SPEED_UNITS_PER_KMH.toDouble()
        unconfirmed = 0
    }

    /** The frames are built in thousandths; for a pad that uses hundredths the speed field is divided by ten. */
    private fun rescale(frame: ByteArray): ByteArray {
        if (speedScale == SpeedScale.THOUSANDTHS || !isSpeedCommand(frame)) return frame
        val raw = rawSpeed(frame)
        if (raw == 0) return frame
        val scaled = (raw.toLong() * speedScale.unitsPerKmh / PitPatProtocol.SPEED_UNITS_PER_KMH).toInt()
        val out = frame.copyOf()
        out[6] = (scaled shr 8).toByte()
        out[7] = scaled.toByte()
        out[out.size - 2] = PitPatProtocol.checksum(out)
        return out
    }

    /** Returns true when it switched the scale, so the caller reads the same frame again. */
    private fun checkScale(status: TreadmillStatus): Boolean {
        val max = status.maxSpeedKmh
        if (max != null && max < PitPatProtocol.speedRange.start) {
            return switchToHundredths("the pad's maximum speed reads ${"%.2f".format(Locale.ROOT, max)} km/h as thousandths")
        }
        val commanded = commandedKmh ?: return false
        if (status.targetSpeedKmh <= 0.0) return false
        val ratio = status.targetSpeedKmh / commanded
        when {
            ratio in ECHO_TENTH -> return switchToHundredths(
                "the pad echoed a target of ${"%.2f".format(Locale.ROOT, status.targetSpeedKmh)} km/h " +
                    "for a commanded ${"%.1f".format(Locale.ROOT, commanded)}",
            )
            ratio in ECHO_SAME -> {
                log("speed scale: thousandths (the pad echoed the commanded target)")
                stopVerifying()
            }
            ++unconfirmed >= MAX_UNCONFIRMED -> {
                log(
                    "speed scale: thousandths, not confirmed (last echo ${"%.2f".format(Locale.ROOT, status.targetSpeedKmh)} " +
                        "km/h for a commanded ${"%.1f".format(Locale.ROOT, commanded)})",
                )
                stopVerifying()
            }
        }
        return false
    }

    private fun switchToHundredths(reason: String): Boolean {
        speedScale = SpeedScale.HUNDREDTHS
        log("speed scale: hundredths ($reason)")
        stopVerifying()
        return true
    }

    private fun stopVerifying() {
        verifying = false
        commandedKmh = null
    }

    private companion object {
        const val START_BYTE: Byte = 0x6A
        const val OP_START = 0x04
        const val CONTROL_FRAME_SIZE = 23

        /** The shortest frame that can carry a length byte and a checksum. */
        const val MIN_FRAME_SIZE = 5

        /** Echo within this share of the commanded speed counts as the same (the pad rounds to 0.1 km/h). */
        val ECHO_SAME = 0.9..1.1

        /** A tenth, with room for rounding. A thousandths pad can never get here: its echo is at least 1/6. */
        val ECHO_TENTH = 0.07..0.14

        /** Statuses to wait for a matching echo before giving up on confirming. */
        const val MAX_UNCONFIRMED = 20
    }
}
