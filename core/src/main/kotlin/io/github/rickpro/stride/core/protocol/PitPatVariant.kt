package io.github.rickpro.stride.core.protocol

import java.util.UUID

/**
 * The ways a PitPat pad exposes its command and status characteristics. The frames inside are the same in every
 * variant (see [PitPatProtocol]); what differs is where they are written, whether each write and notification
 * carries the 4-byte `4D 00` header, and how the pad is kept alive. Details: docs/protocol.md.
 *
 * The order is the detection priority: the first variant a pad offers wins.
 */
enum class PitPatVariant(
    /** Shown in Settings and in the diagnostics log. */
    val displayName: String,
    /** The service that must hold both characteristics, or null to accept whichever service holds them. */
    val requiredService: UUID?,
    val writeUuid: UUID,
    val notifyUuid: UUID,
    /** Every write and notification carries the header `4D 00 <counter> <length>` (see [PitPatWrapper]). */
    val wrapped: Boolean,
) {
    /** Service FBA0, as on the PitPat-T01 family the protocol was first documented on. */
    CLASSIC("PitPat", uuid16(0xFBA0), uuid16(0xFBA1), uuid16(0xFBA2), wrapped = false),

    /** FF01 / FF02 with the wrapper. Seen in service FFFF (qdomyos) and as the only pair in azmke's client. */
    WRAPPED("PitPat wrapped", null, uuid16(0xFF01), uuid16(0xFF02), wrapped = true),

    /** FFF1 / FFF2 (service FFF0) with bare frames and no wrapper. */
    PLAIN_FFF("PitPat FFF", null, uuid16(0xFFF1), uuid16(0xFFF2), wrapped = false),
    ;

    /** True when the pad wants a heartbeat after each notification, not a timer. */
    val heartbeatPerNotification: Boolean get() = wrapped

    /** The speed scale is only questioned on variants that were not verified on a real pad by the port's source. */
    val verifiesSpeedScale: Boolean get() = this != CLASSIC
}

/** The result of looking through a pad's GATT table. */
data class DetectedTransport(
    val variant: PitPatVariant,
    val serviceUuid: UUID,
    /** The characteristic that receives the frames. */
    val writeUuid: UUID,
    /** The characteristic that is subscribed to for status. */
    val notifyUuid: UUID,
    val writeProperties: Int,
    val notifyProperties: Int,
) {
    fun describe(): String =
        "${variant.displayName}: service ${GattTable.label(serviceUuid)}, " +
            "write ${GattTable.label(writeUuid)}, notify ${GattTable.label(notifyUuid)}"
}

object PitPatTransports {
    /**
     * Picks the first variant the pad offers: FBA0, else FF01/FF02, else FFF1/FFF2. For the last two, the
     * characteristic that can be written is the command one and the one that can notify is the status one;
     * if the two are the other way round on a pad, the roles are swapped. Returns null when nothing fits.
     */
    fun detect(services: List<GattServiceInfo>): DetectedTransport? {
        for (variant in PitPatVariant.entries) {
            for (service in services) {
                if (variant.requiredService != null && service.uuid != variant.requiredService) continue
                match(variant, service)?.let { return it }
            }
        }
        return null
    }

    private fun match(variant: PitPatVariant, service: GattServiceInfo): DetectedTransport? {
        val first = service.characteristics.firstOrNull { it.uuid == variant.writeUuid } ?: return null
        val second = service.characteristics.firstOrNull { it.uuid == variant.notifyUuid } ?: return null
        val (write, notify) = when {
            variant == PitPatVariant.CLASSIC -> first to second // as documented; no property check, as before
            first.canWrite && second.canNotify -> first to second
            second.canWrite && first.canNotify -> second to first
            else -> return null
        }
        return DetectedTransport(variant, service.uuid, write.uuid, notify.uuid, write.properties, notify.properties)
    }
}
