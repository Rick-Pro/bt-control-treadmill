package io.github.rickpro.stride.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Handler
import io.github.rickpro.stride.core.protocol.TreadmillProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * One GATT session with the treadmill: connect, raise the MTU, discover services and subscribe to the status
 * characteristics; then write command frames one at a time.
 *
 * Android allows a single GATT operation in flight, so every request waits for its callback (or a timeout)
 * before the next one starts. Bluetooth callbacks arrive on [handler]'s thread and every method must be called
 * from that same thread, so no locking is needed.
 *
 * Ported from Franrodr10/pitpat-controller (MIT), see THIRD_PARTY_NOTICES.md.
 */
@SuppressLint("MissingPermission") // only created after BlePermissions.granted()
class GattConnection(
    private val context: Context,
    private val device: BluetoothDevice,
    private val protocol: TreadmillProtocol,
    private val handler: Handler,
    private val listener: Listener,
) {
    interface Listener {
        fun onData(characteristic: UUID, value: ByteArray)

        /** The link went down after [open] succeeded. Not called after [close]. */
        fun onDisconnected(status: Int)
    }

    enum class WriteResult { OK, TIMEOUT, BUSY, FAILED, NOT_CONNECTED }

    open class GattException(message: String) : Exception(message)

    class IncompatibleDeviceException(message: String) : GattException(message)

    var mtu = DEFAULT_MTU
        private set

    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var linkUp: CompletableDeferred<Unit>? = null
    private val mtuOp = Operation()
    private val discoveryOp = Operation()
    private val descriptorOp = Operation()
    private val writeOp = Operation()
    private var ready = false
    private var closed = false

    /** Connects and prepares the link. Throws [GattException] on failure; call [close] afterwards. */
    suspend fun open() {
        val connected = CompletableDeferred<Unit>()
        linkUp = connected
        // Deprecated in the newest SDKs, but it is the overload that delivers callbacks on our own thread.
        @Suppress("DEPRECATION")
        val g = device.connectGatt(
            context, false, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, handler,
        ) ?: throw GattException("Android could not start the connection")
        gatt = g
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } ?: throw GattException("the treadmill does not answer")

        // Short connection interval: the treadmill drops idle links with the default parameters.
        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        delay(SETTLE_MS)
        // A larger MTU lets a whole status frame fit in one notification; the decoder copes without.
        mtuOp.run(OP_TIMEOUT_MS) { g.requestMtu(MAX_MTU) }

        val discovery = discoveryOp.run(DISCOVERY_TIMEOUT_MS) { g.discoverServices() }
        if (discovery != BluetoothGatt.GATT_SUCCESS) throw GattException("could not read the services (${describe(discovery)})")
        val service = g.getService(protocol.service)
            ?: throw IncompatibleDeviceException("the device has no service ${protocol.service}")
        commandCharacteristic = service.getCharacteristic(protocol.commandCharacteristic)
            ?: throw IncompatibleDeviceException("the command characteristic ${protocol.commandCharacteristic} is missing")
        for (uuid in protocol.statusCharacteristics) {
            val characteristic = service.getCharacteristic(uuid)
                ?: throw IncompatibleDeviceException("the status characteristic $uuid is missing")
            enableUpdates(g, characteristic)
        }
        ready = true
    }

    /** Writes one command frame and waits until the treadmill acknowledges it. */
    suspend fun write(frame: ByteArray): WriteResult {
        val g = gatt
        val characteristic = commandCharacteristic
        if (g == null || characteristic == null || !ready || closed) return WriteResult.NOT_CONNECTED
        return try {
            repeat(BUSY_RETRIES) {
                when (writeOp.run(WRITE_TIMEOUT_MS) { startWrite(g, characteristic, frame) }) {
                    BluetoothGatt.GATT_SUCCESS -> return WriteResult.OK
                    Operation.NOT_STARTED -> delay(BUSY_RETRY_MS) // the stack is still busy with a late callback
                    Operation.TIMED_OUT -> return WriteResult.TIMEOUT
                    else -> return WriteResult.FAILED
                }
            }
            WriteResult.BUSY
        } catch (_: GattException) {
            WriteResult.NOT_CONNECTED
        } catch (_: SecurityException) {
            WriteResult.FAILED
        }
    }

    fun close() {
        if (closed) return
        closed = true
        ready = false
        val g = gatt ?: return
        gatt = null
        try {
            g.disconnect()
            g.close()
        } catch (_: SecurityException) {
            // Permission revoked: Android has already dropped the link.
        }
    }

    private suspend fun enableUpdates(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!g.setCharacteristicNotification(characteristic, true)) {
            throw GattException("could not turn on notifications for ${characteristic.uuid}")
        }
        val cccd = characteristic.getDescriptor(CCCD) ?: return
        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        val status = descriptorOp.run(OP_TIMEOUT_MS) { g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS }
        if (status != BluetoothGatt.GATT_SUCCESS) throw GattException("could not turn on notifications (${describe(status)})")
    }

    private fun startWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, frame: ByteArray): Boolean =
        g.writeCharacteristic(characteristic, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
            BluetoothStatusCodes.SUCCESS

    private fun deliver(uuid: UUID, value: ByteArray) {
        if (!closed) listener.onData(uuid, value.copyOf())
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) linkUp?.complete(Unit) else g.disconnect()
                return
            }
            if (newState != BluetoothProfile.STATE_DISCONNECTED) return
            val cause = GattException("connection closed (${describe(status)})")
            linkUp?.completeExceptionally(cause)
            for (op in listOf(mtuOp, discoveryOp, descriptorOp, writeOp)) op.fail(cause)
            val wasReady = ready
            close()
            if (wasReady) listener.onDisconnected(status)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) this@GattConnection.mtu = mtu
            mtuOp.complete(status)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = discoveryOp.complete(status)

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
            descriptorOp.complete(status)

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
            writeOp.complete(status)

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) =
            deliver(characteristic.uuid, value)
    }

    /** One GATT request in flight and the callback that completes it. */
    private class Operation {
        private var result: CompletableDeferred<Int>? = null

        /** Returns the callback status, [NOT_STARTED] if Android refused the request or [TIMED_OUT]. */
        suspend fun run(timeoutMs: Long, start: () -> Boolean): Int {
            val deferred = CompletableDeferred<Int>()
            result = deferred
            try {
                if (!start()) return NOT_STARTED
                return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: TIMED_OUT
            } finally {
                if (result === deferred) result = null
            }
        }

        fun complete(status: Int) {
            result?.complete(status)
        }

        fun fail(cause: Throwable) {
            result?.completeExceptionally(cause)
        }

        companion object {
            const val NOT_STARTED = -1
            const val TIMED_OUT = -2
        }
    }

    companion object {
        private const val DEFAULT_MTU = 23
        private const val MAX_MTU = 517
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val SETTLE_MS = 200L
        private const val OP_TIMEOUT_MS = 3_000L
        private const val DISCOVERY_TIMEOUT_MS = 10_000L
        private const val WRITE_TIMEOUT_MS = 1_500L
        private const val BUSY_RETRIES = 20
        private const val BUSY_RETRY_MS = 25L
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Human-readable GATT status for the diagnostics log. */
        fun describe(status: Int): String = when (status) {
            BluetoothGatt.GATT_SUCCESS -> "ok"
            8 -> "8: the treadmill stopped answering"
            19 -> "19: the treadmill closed the connection"
            22 -> "22: the phone closed the connection"
            62 -> "62: could not be established"
            133 -> "133: GATT error"
            Operation.NOT_STARTED -> "Android refused the request"
            Operation.TIMED_OUT -> "no answer"
            else -> "status $status"
        }
    }
}
