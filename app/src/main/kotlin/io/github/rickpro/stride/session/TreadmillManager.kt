package io.github.rickpro.stride.session

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.rickpro.stride.ble.BlePermissions
import io.github.rickpro.stride.ble.GattConnection
import io.github.rickpro.stride.ble.GattConnection.WriteResult
import io.github.rickpro.stride.core.control.Command
import io.github.rickpro.stride.core.control.CommandKind
import io.github.rickpro.stride.core.control.CommandScheduler
import io.github.rickpro.stride.core.control.SessionController
import io.github.rickpro.stride.core.control.StepEstimator
import io.github.rickpro.stride.core.history.WorkoutRecorder
import io.github.rickpro.stride.core.protocol.PitPatLink
import io.github.rickpro.stride.core.protocol.PitPatProtocol
import io.github.rickpro.stride.core.protocol.RunState
import io.github.rickpro.stride.core.protocol.TreadmillProtocol
import io.github.rickpro.stride.core.protocol.TreadmillStatus
import io.github.rickpro.stride.core.protocol.toHex
import io.github.rickpro.stride.core.units.MeasureUnit
import io.github.rickpro.stride.history.HistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Owns the link with the treadmill for the whole app (it outlives the activity) and exposes its state to the UI.
 *
 * Safety rules live in [SessionController]; this class adds the transport side: one write at a time with STOP
 * first, keep-alive, a watchdog for silent links, and reconnection with a vibration and a visible alert when the
 * link drops. Nothing is resent after reconnecting. Every decoded status also feeds the [WorkoutRecorder]; a
 * finished walk goes to the [HistoryStore].
 *
 * Lifecycle, called by MainActivity: [onForeground] in onStart (reconnects to the remembered treadmill),
 * [onBackground] in onStop (keeps a live link, stops retrying a dead one), [onAppClosed] in onDestroy when
 * isFinishing (lets the treadmill go unless the belt is moving).
 *
 * All mutable state is confined to a dedicated Bluetooth thread; public methods only post to it.
 *
 * Ported from Franrodr10/pitpat-controller (MIT), see THIRD_PARTY_NOTICES.md.
 */
@SuppressLint("MissingPermission") // Bluetooth is only used after BlePermissions.granted()
class TreadmillManager(
    private val context: Context,
    private val settings: Settings,
    val log: DiagnosticsLog,
    private val haptics: Haptics,
    private val historyStore: HistoryStore,
    val appVersion: String,
) {
    enum class Link { NO_DEVICE, DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

    enum class Alert { LINK_LOST, LINK_LOST_WHILE_MOVING, STOP_NOT_CONFIRMED }

    enum class Message { NOT_CONNECTED, NO_DATA, RECONNECTED, INCOMPATIBLE }

    data class State(
        val link: Link,
        val deviceName: String?,
        val attempt: Int = 0,
        val bluetoothOn: Boolean,
        val status: TreadmillStatus? = null,
        /** True while the last status is less than 3 s old. */
        val fresh: Boolean = false,
        val steps: StepEstimator.Steps? = null,
        val targetKmh: Double,
        val speedRange: ClosedFloatingPointRange<Double>,
        val unit: MeasureUnit,
        val presetsKmh: List<Double>,
        /** True while a walk is open in the recorder (it will land in History when it ends). */
        val recording: Boolean = false,
        val alert: Alert? = null,
        /** Name of the PitPat transport in use (or last used), for Settings; null until a pad has connected. */
        val transport: String? = null,
    ) {
        val isActive: Boolean get() = link == Link.CONNECTING || link == Link.CONNECTED || link == Link.RECONNECTING
        val isConnected: Boolean get() = link == Link.CONNECTED
    }

    private val protocol: TreadmillProtocol = PitPatProtocol
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val bleThread = HandlerThread("stride-ble").apply { start() }
    private val handler = Handler(bleThread.looper)
    private val scope = CoroutineScope(SupervisorJob() + handler.asCoroutineDispatcher())

    // Only touched on the Bluetooth thread.
    private val controller = SessionController(protocol, settings.targetKmh)
    private val scheduler = CommandScheduler()
    private val wakeWriter = Channel<Unit>(Channel.CONFLATED)
    private val stepEstimator = StepEstimator()
    private val recorder = WorkoutRecorder()
    private val incomingLog = IncomingLog()
    /** Wire format of the current connection (header, counter, speed scale); null while not connected. */
    private var codec: PitPatLink? = null
    private var session: Job? = null
    private var linkLost: CompletableDeferred<Unit>? = null
    private var writing = false
    private var lastDataAtMs = 0L
    private var stopFailures = 0

    private val _state = MutableStateFlow(
        State(
            link = if (settings.deviceAddress == null) Link.NO_DEVICE else Link.DISCONNECTED,
            deviceName = settings.deviceName,
            bluetoothOn = adapter?.isEnabled == true,
            targetKmh = controller.targetKmh,
            speedRange = controller.speedRange,
            unit = settings.unit,
            presetsKmh = settings.presetsKmh,
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 8)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val on = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON
            if (on != _state.value.bluetoothOn) log.add(if (on) "Bluetooth turned on" else "Bluetooth turned off")
            _state.update { it.copy(bluetoothOn = on) }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context, bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        log.add("Stride $appVersion, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
    }

    // ------------------------------------------------------------------ public API (any thread)

    /** Remembers [address] as the treadmill and connects to it. */
    fun connect(address: String, name: String?) = onBle {
        settings.saveDevice(address, name)
        _state.update { it.copy(deviceName = name, alert = null) }
        startSession()
    }

    /** The app came to the foreground: reconnect to the remembered treadmill if needed. */
    fun onForeground() = onBle {
        _state.update { it.copy(bluetoothOn = adapter?.isEnabled == true) }
        if (session?.isActive != true && settings.deviceAddress != null) startSession()
    }

    /** The app went to the background: keep a live link, but stop retrying a dead one. */
    fun onBackground() = onBle {
        if (_state.value.link != Link.CONNECTED) stopSession()
    }

    /** The activity was closed for good: let the treadmill go unless the belt is moving. */
    fun onAppClosed() = onBle {
        if (_state.value.status?.isMoving != true) {
            stopSession()
            // A walk left open (for example paused) would otherwise be lost with the process.
            recorder.flush(System.currentTimeMillis())?.let { finished ->
                historyStore.add(finished)
                log.add("Walk saved: ${finished.durationSeconds} s, ${"%.2f".format(Locale.ROOT, finished.distanceKm)} km")
            }
            _state.update { it.copy(recording = false) }
        }
    }

    fun reconnect() = onBle { if (settings.deviceAddress != null) startSession() }

    fun forget() = onBle {
        stopSession()
        settings.forgetDevice()
        _state.update { it.copy(link = Link.NO_DEVICE, deviceName = null, transport = null) }
    }

    fun startOrPause() = onBle { handle(controller.startOrPause(isConnected(), now())) }

    fun stop() = onBle {
        val outcome = controller.stop(isConnected(), now())
        if (outcome.notice == SessionController.Notice.NOT_CONNECTED) haptics.alert()
        handle(outcome)
    }

    /** Changes the target by [deltaKmh]; the controller rounds to the treadmill's 0.1 km/h step. */
    fun changeSpeed(deltaKmh: Double) = onBle { handle(controller.changeSpeed(deltaKmh, isConnected(), now())) }

    fun selectSpeed(kmh: Double) = onBle { handle(controller.selectSpeed(kmh, isConnected(), now())) }

    /** Experimental: the treadmill's own beeper, from a packet captured on a sibling model. */
    fun setBeeper(on: Boolean) = onBle {
        if (!isConnected()) {
            _messages.tryEmit(Message.NOT_CONNECTED)
            return@onBle
        }
        scheduler.submit(Command(CommandKind.CONTROL, PitPatProtocol.beeperFrame(on), if (on) "beeper on" else "beeper off"))
        wakeWriter.trySend(Unit)
    }

    fun setUnit(unit: MeasureUnit) = onBle {
        settings.unit = unit
        _state.update { it.copy(unit = unit) }
    }

    fun setPresets(presetsKmh: List<Double>) = onBle {
        val clean = presetsKmh.map { (it * 10).roundToInt() / 10.0 }.take(Settings.DEFAULT_PRESETS_KMH.size)
        if (clean.size != Settings.DEFAULT_PRESETS_KMH.size) return@onBle
        settings.presetsKmh = clean
        _state.update { it.copy(presetsKmh = clean) }
    }

    fun dismissAlert() = onBle { _state.update { it.copy(alert = null) } }

    /** The app version and the whole diagnostics log, for Share. */
    fun diagnosticsText(): String {
        val header = listOfNotNull("Stride $appVersion diagnostics", _state.value.transport?.let { "Protocol: $it" })
        return (header + log.snapshot()).joinToString("\n") + "\n"
    }

    private fun onBle(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    // ------------------------------------------------------------------ session

    private fun isConnected() = _state.value.link == Link.CONNECTED

    /** Monotonic clock for timeouts; never the wall clock. */
    private fun now() = SystemClock.elapsedRealtime()

    private suspend fun startSession() {
        session?.cancelAndJoin()
        session = scope.launch { runSession() }
    }

    private suspend fun stopSession() {
        session?.cancelAndJoin()
        session = null
        _state.update {
            it.copy(
                link = if (settings.deviceAddress == null) Link.NO_DEVICE else Link.DISCONNECTED,
                attempt = 0, status = null, fresh = false, steps = null, alert = null,
            )
        }
    }

    /** Connects, then reconnects with backoff every time the link drops, until cancelled. */
    private suspend fun runSession() {
        val address = settings.deviceAddress ?: return
        var attempt = 0
        while (true) {
            _state.update { it.copy(link = if (attempt == 0) Link.CONNECTING else Link.RECONNECTING, attempt = attempt) }
            val bluetooth = adapter
            if (bluetooth == null || !bluetooth.isEnabled || !BlePermissions.granted(context)) {
                delay(1_000) // the UI is asking for Bluetooth or the permission meanwhile
                continue
            }
            var connection: GattConnection? = null
            var linkWasUp = false
            try {
                connection = GattConnection(context, bluetooth.getRemoteDevice(address), handler, gattListener, log::add)
                log.add("Connecting to ${settings.deviceName ?: protocol.name}")
                connection.open()
                linkWasUp = true
                attempt = 0
                onConnected(connection)
                awaitLinkLoss(connection)
            } catch (e: GattConnection.IncompatibleDeviceException) {
                log.add("!! Not a supported treadmill: ${e.message}")
                _messages.tryEmit(Message.INCOMPATIBLE)
                _state.update { it.copy(link = Link.DISCONNECTED, attempt = 0) }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.add("!! Could not connect: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                connection?.close()
                if (linkWasUp) onLinkDown(byUser = !currentCoroutineContext().isActive)
            }
            attempt++
            // Not connected any more from this moment: STOP must warn instead of queuing silently.
            _state.update { it.copy(link = Link.RECONNECTING, attempt = attempt) }
            delay(minOf(1_000L shl minOf(attempt - 1, 4), MAX_BACKOFF_MS))
        }
    }

    private val gattListener = object : GattConnection.Listener {
        override fun onData(characteristic: UUID, value: ByteArray) = onIncoming(value)

        override fun onDisconnected(status: Int) {
            log.add("!! Connection lost (${GattConnection.describe(status)})")
            linkLost?.complete(Unit)
        }
    }

    private fun onConnected(connection: GattConnection) {
        val variant = checkNotNull(connection.transport) { "connected without a transport" }.variant
        codec = PitPatLink(variant, log::add)
        stepEstimator.reset()
        scheduler.clear()
        incomingLog.reset()
        lastDataAtMs = now()
        val previous = _state.value.alert
        val wasLost = previous == Alert.LINK_LOST || previous == Alert.LINK_LOST_WHILE_MOVING
        log.add(
            "Connected: ${variant.displayName} (MTU ${connection.mtu}, " +
                "${if (variant.heartbeatPerNotification) "heartbeat" else "keep-alive"} ${if (settings.keepAliveEnabled) "on" else "off"})",
        )
        _state.update {
            it.copy(link = Link.CONNECTED, attempt = 0, alert = if (wasLost) null else it.alert, transport = variant.displayName)
        }
        if (wasLost) _messages.tryEmit(Message.RECONNECTED)
    }

    /** Runs the writer, keep-alive and watchdog until the link is lost. */
    private suspend fun awaitLinkLoss(connection: GattConnection) = coroutineScope {
        val lost = CompletableDeferred<Unit>()
        linkLost = lost
        val workers = listOf(
            launch { writerLoop(connection) },
            launch { keepAliveLoop() },
            launch { watchdogLoop(lost) },
        )
        try {
            lost.await()
        } finally {
            workers.forEach { it.cancel() }
            linkLost = null
        }
    }

    /** Forgets everything pending: after a reconnection nothing is resent on its own. */
    private fun onLinkDown(byUser: Boolean) {
        val wasMoving = _state.value.status?.isMoving == true
        scheduler.clear()
        controller.onLinkDown()
        codec = null
        recorder.onLinkDown(System.currentTimeMillis())?.let { finished ->
            historyStore.add(finished)
            log.add("Walk saved: ${finished.durationSeconds} s, ${"%.2f".format(Locale.ROOT, finished.distanceKm)} km")
        }
        _state.update {
            it.copy(status = null, fresh = false, steps = null, speedRange = controller.speedRange, recording = recorder.isRecording)
        }
        if (!byUser) {
            _state.update { it.copy(alert = if (wasMoving) Alert.LINK_LOST_WHILE_MOVING else Alert.LINK_LOST) }
            haptics.alert()
        }
    }

    // ------------------------------------------------------------------ data in, commands out

    private fun onIncoming(value: ByteArray) {
        val now = now()
        lastDataAtMs = now
        val statuses = codec?.decode(value, now).orEmpty()
        incomingLog.line(value, statuses.lastOrNull(), now)?.let(log::add)
        for (status in statuses) {
            handle(controller.onStatus(status, now))
            val steps = stepEstimator.update(status)
            recorder.update(status, steps, System.currentTimeMillis())?.let { finished ->
                historyStore.add(finished)
                log.add("Walk saved: ${finished.durationSeconds} s, ${"%.2f".format(Locale.ROOT, finished.distanceKm)} km")
            }
            _state.update {
                it.copy(
                    status = status, fresh = true, steps = steps, recording = recorder.isRecording,
                    alert = if (it.alert == Alert.STOP_NOT_CONFIRMED && !status.isMoving) null else it.alert,
                )
            }
        }
        answerNotification()
    }

    /**
     * A wrapped pad is kept alive by answering each of its notifications with one heartbeat, not by a timer. The
     * heartbeat is skipped when a command is queued or being written, so it never delays a real command.
     */
    private fun answerNotification() {
        if (codec?.heartbeatPerNotification != true) return
        queueKeepAlive("heartbeat")
    }

    private fun queueKeepAlive(label: String) {
        val frame = protocol.keepAliveFrame ?: return
        if (!settings.keepAliveEnabled || writing || !scheduler.isEmpty) return
        scheduler.submit(Command(CommandKind.KEEP_ALIVE, frame, label))
        wakeWriter.trySend(Unit)
    }

    private fun handle(outcome: SessionController.Outcome) {
        if (outcome.commands.isNotEmpty()) {
            outcome.commands.forEach(scheduler::submit)
            wakeWriter.trySend(Unit)
        }
        when (outcome.notice) {
            SessionController.Notice.NOT_CONNECTED -> _messages.tryEmit(Message.NOT_CONNECTED)
            SessionController.Notice.NO_DATA -> _messages.tryEmit(Message.NO_DATA)
            SessionController.Notice.STOP_NOT_CONFIRMED -> stopNotConfirmed()
            null -> Unit
        }
        settings.targetKmh = controller.targetKmh
        _state.update { it.copy(targetKmh = controller.targetKmh, speedRange = controller.speedRange) }
    }

    private fun stopNotConfirmed() {
        log.add("!! The treadmill did not confirm the stop")
        _state.update { it.copy(alert = Alert.STOP_NOT_CONFIRMED) }
        haptics.alert()
    }

    private suspend fun writerLoop(connection: GattConnection) {
        while (true) {
            val command = scheduler.next()
            if (command == null) {
                wakeWriter.receive()
                continue
            }
            // What goes on the wire: the frame itself, or for a wrapped pad the frame with its header and counter.
            val wire = (codec ?: return).encode(command.frame)
            writing = true
            val result = try {
                connection.write(wire)
            } finally {
                writing = false
            }
            if (command.kind != CommandKind.KEEP_ALIVE || result != WriteResult.OK) {
                log.add("-> ${wire.toHex()} (${command.label}) ${result.name.lowercase(Locale.ROOT)}")
            }
            if (command.kind == CommandKind.STOP) onStopWritten(command, result)
        }
    }

    private suspend fun onStopWritten(command: Command, result: WriteResult) {
        if (result == WriteResult.OK) {
            stopFailures = 0
            return
        }
        stopFailures++
        if (stopFailures <= MAX_STOP_RETRIES) {
            delay(100)
            scheduler.submit(command)
        } else {
            stopFailures = 0
            stopNotConfirmed()
        }
    }

    private suspend fun keepAliveLoop() {
        if (protocol.keepAliveFrame == null) return
        if (codec?.heartbeatPerNotification == true) return // answered after each notification instead, see above
        while (true) {
            delay(protocol.keepAliveIntervalMs)
            queueKeepAlive("keep-alive")
        }
    }

    /** Some links die without Android noticing for a long time: no data for a while means lost. */
    private suspend fun watchdogLoop(lost: CompletableDeferred<Unit>) {
        while (true) {
            delay(1_000)
            val now = now()
            val fresh = controller.hasFreshStatus(now)
            if (_state.value.fresh != fresh) _state.update { it.copy(fresh = fresh) }
            val silentMs = now - lastDataAtMs
            if (silentMs > WATCHDOG_MS) {
                log.add("!! No data from the treadmill for ${silentMs / 1000} s: restarting the connection")
                lost.complete(Unit)
                return
            }
        }
    }

    /** Keeps the diagnostics log readable: frames are logged when something but the counters changes. */
    private class IncomingLog {
        private var lastKey: String? = null
        private var lastAtMs = 0L
        private var skipped = 0
        private var undecoded = 0

        fun reset() {
            lastKey = null
            skipped = 0
            undecoded = 0
        }

        fun line(raw: ByteArray, status: TreadmillStatus?, nowMs: Long): String? {
            if (status == null) {
                undecoded++
                return if (undecoded <= 20 || undecoded % 50 == 0) "<- ${raw.toHex()}" else null
            }
            val key = "${status.state}/${status.targetSpeedKmh}/${(status.speedKmh * 10).roundToInt()}"
            if (key == lastKey && nowMs - lastAtMs < 5_000) {
                skipped++
                return null
            }
            val extra = if (skipped > 0) " (+$skipped)" else ""
            lastKey = key
            lastAtMs = nowMs
            skipped = 0
            return "<- ${raw.toHex()} | ${describe(status)}$extra"
        }

        private fun describe(s: TreadmillStatus): String {
            val state = when (s.state) {
                RunState.STOPPED -> "stopped"
                RunState.STARTING -> "starting"
                RunState.RUNNING -> "running"
                RunState.PAUSED -> "paused"
            }
            return String.format(
                Locale.ROOT, "%s v=%.2f target=%.2f d=%.3f t=%d steps=%d kcal=%d max=%s",
                state, s.speedKmh, s.targetSpeedKmh, s.distanceKm, s.durationSeconds, s.steps, s.kcal,
                s.maxSpeedKmh?.toString() ?: "-",
            )
        }
    }

    private companion object {
        const val WATCHDOG_MS = 10_000L
        const val MAX_BACKOFF_MS = 10_000L
        const val MAX_STOP_RETRIES = 3
    }
}
