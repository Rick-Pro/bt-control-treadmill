package io.github.rickpro.stride.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.rickpro.stride.core.protocol.PitPatProtocol
import io.github.rickpro.stride.core.protocol.TreadmillProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Finds the treadmill. Android has no name-prefix scan filter, so the scan is unfiltered and every result is
 * matched against the protocol (advertised name or service UUID). Runs only while the Setup screen is open.
 */
@SuppressLint("MissingPermission") // only started after BlePermissions.granted()
class BleScanner(context: Context, private val protocol: TreadmillProtocol = PitPatProtocol) {

    data class Found(val address: String, val name: String?, val rssi: Int)

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopLater = Runnable { stop() }
    private var callback: ScanCallback? = null

    private val _results = MutableStateFlow<List<Found>>(emptyList())
    val results: StateFlow<List<Found>> = _results.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    /** A [ScanCallback] error code, or null. */
    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    fun start(durationMs: Long = 15_000) {
        stop()
        _results.value = emptyList()
        _error.value = null
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = add(result)

            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::add)

            override fun onScanFailed(errorCode: Int) {
                _error.value = errorCode
                stop()
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (_: SecurityException) {
            return
        }
        callback = scanCallback
        _scanning.value = true
        mainHandler.postDelayed(stopLater, durationMs)
    }

    fun stop() {
        mainHandler.removeCallbacks(stopLater)
        val scanCallback = callback ?: return
        callback = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            // Permission revoked while scanning: nothing left to stop.
        }
        _scanning.value = false
    }

    private fun add(result: ScanResult) {
        val record = result.scanRecord
        val name = record?.deviceName ?: result.device.name
        val services = record?.serviceUuids.orEmpty().map { it.uuid }
        if (!protocol.matchesAdvertisement(name, services)) return
        val found = Found(result.device.address, name, result.rssi)
        _results.update { list -> (list.filterNot { it.address == found.address } + found).sortedByDescending { it.rssi } }
    }
}
