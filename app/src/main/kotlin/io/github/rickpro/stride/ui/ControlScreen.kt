package io.github.rickpro.stride.ui

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.rickpro.stride.R
import io.github.rickpro.stride.core.control.StartCountdown
import io.github.rickpro.stride.core.protocol.RunState
import io.github.rickpro.stride.core.units.MeasureUnit
import io.github.rickpro.stride.core.units.Units
import io.github.rickpro.stride.session.TreadmillManager
import io.github.rickpro.stride.ui.components.Banner
import io.github.rickpro.stride.ui.components.StatTile
import io.github.rickpro.stride.ui.components.StrideIconButton
import io.github.rickpro.stride.ui.components.openBluetoothSettings
import io.github.rickpro.stride.ui.theme.StrideColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One step of the treadmill's own speed granularity, in km/h. */
private const val STEP_KMH = 0.1

/**
 * The home screen: status line, the big speed number, four stats, then the control cluster in the lower
 * half where the thumb lives: SPD -, Start/Pause, SPD +, a wide STOP, four preset chips, and a small row
 * with the units toggle, History and Settings.
 */
@Composable
fun ControlScreen(
    state: TreadmillManager.State,
    manager: TreadmillManager,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onFind: () -> Unit,
) {
    val context = LocalContext.current
    val status = state.status
    val connected = state.link == TreadmillManager.Link.CONNECTED
    val ready = connected && state.fresh && status != null
    val unit = state.unit

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        StatusLine(state = state, manager = manager, onFind = onFind)

        if (!permissionGranted) {
            Banner(
                text = stringResource(R.string.banner_permission),
                actionLabel = stringResource(R.string.btn_allow),
                onAction = onRequestPermission,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else if (!state.bluetoothOn) {
            Banner(
                text = stringResource(R.string.link_bluetooth_off),
                actionLabel = stringResource(R.string.btn_open_bluetooth),
                onAction = { openBluetoothSettings(context) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else if (state.alert == TreadmillManager.Alert.LINK_LOST || state.link == TreadmillManager.Link.DISCONNECTED) {
            Banner(
                text = stringResource(if (state.disconnectedByUser) R.string.banner_disconnected else R.string.banner_link_lost),
                actionLabel = stringResource(R.string.btn_reconnect),
                onAction = {
                    manager.dismissAlert()
                    manager.reconnect()
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        SpeedReadout(state = state)

        Spacer(Modifier.height(20.dp))

        StatsRow(state = state)

        Spacer(Modifier.weight(1.2f))

        ControlCluster(state = state, ready = ready, manager = manager)

        Spacer(Modifier.height(14.dp))

        PresetRow(state = state, enabled = connected, manager = manager)

        Spacer(Modifier.height(10.dp))

        BottomRow(unit = unit, manager = manager, onHistory = onHistory, onSettings = onSettings)

        Spacer(Modifier.height(8.dp))
    }
}

private enum class Confirm { DISCONNECT, FIND, FORGET }

/**
 * A small dot plus the treadmill's name and link state. Tap it for the connection menu: Reconnect, Disconnect,
 * Find a treadmill and Forget treadmill. While reconnecting it shows the attempt number and the last problem.
 */
@Composable
private fun StatusLine(state: TreadmillManager.State, manager: TreadmillManager, onFind: () -> Unit) {
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val moving = state.status?.isMoving == true
    val (label, color) = when {
        !state.bluetoothOn -> stringResource(R.string.link_bluetooth_off) to StrideColors.Warning
        state.link == TreadmillManager.Link.CONNECTED && state.fresh -> stringResource(R.string.link_connected) to StrideColors.Accent
        state.link == TreadmillManager.Link.CONNECTED -> stringResource(R.string.hint_no_data) to StrideColors.Warning
        state.link == TreadmillManager.Link.CONNECTING -> stringResource(R.string.link_connecting) to StrideColors.Warning
        state.link == TreadmillManager.Link.RECONNECTING && state.attempt > 0 ->
            stringResource(R.string.link_reconnecting_attempt, state.attempt) to StrideColors.Warning
        state.link == TreadmillManager.Link.RECONNECTING -> stringResource(R.string.link_reconnecting) to StrideColors.Warning
        state.disconnectedByUser -> stringResource(R.string.link_disconnected_by_user) to StrideColors.Muted
        else -> stringResource(R.string.link_disconnected) to StrideColors.Muted
    }
    val problem = state.lastError.takeIf { state.link != TreadmillManager.Link.CONNECTED && !state.disconnectedByUser }

    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = stringResource(R.string.menu_connection), role = Role.Button) { menuOpen = true }
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.deviceName ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = StrideColors.Text,
                    maxLines = 1,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    maxLines = 1,
                )
                if (problem != null) {
                    Text(
                        text = stringResource(R.string.link_last_problem, problem),
                        style = MaterialTheme.typography.bodySmall,
                        color = StrideColors.Muted,
                        maxLines = 2,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = StrideColors.Muted,
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = StrideColors.SurfaceHigh,
        ) {
            val remembered = state.link != TreadmillManager.Link.NO_DEVICE
            DropdownMenuItem(
                text = { Text(stringResource(R.string.btn_reconnect), color = StrideColors.Text) },
                enabled = remembered,
                onClick = {
                    menuOpen = false
                    manager.dismissAlert()
                    manager.reconnect()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.btn_disconnect), color = StrideColors.Text) },
                enabled = state.isActive,
                onClick = {
                    menuOpen = false
                    if (moving) confirm = Confirm.DISCONNECT else manager.disconnect()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.btn_find_treadmill), color = StrideColors.Text) },
                onClick = {
                    menuOpen = false
                    if (moving) {
                        confirm = Confirm.FIND
                    } else {
                        // Close the link first and only then scan: the pad accepts one connection at a time.
                        scope.launch {
                            manager.disconnectAndWait()
                            onFind()
                        }
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.btn_forget), color = StrideColors.Stop) },
                enabled = remembered,
                onClick = {
                    menuOpen = false
                    confirm = Confirm.FORGET
                },
            )
        }
    }

    confirm?.let { action ->
        ConfirmConnectionAction(
            action = action,
            deviceName = state.deviceName,
            moving = moving,
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                when (action) {
                    Confirm.DISCONNECT -> manager.disconnect()
                    Confirm.FIND -> scope.launch {
                        manager.disconnectAndWait()
                        onFind()
                    }
                    Confirm.FORGET -> manager.forget()
                }
            },
        )
    }
}

/** Forget always asks. Disconnect and Find ask only while the belt moves, because it keeps running unattended. */
@Composable
private fun ConfirmConnectionAction(action: Confirm, deviceName: String?, moving: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val title = when (action) {
        Confirm.FORGET -> stringResource(R.string.confirm_forget_title)
        else -> stringResource(R.string.confirm_moving_title)
    }
    val confirmLabel = when (action) {
        Confirm.DISCONNECT -> stringResource(R.string.btn_disconnect)
        Confirm.FIND -> stringResource(R.string.btn_find_treadmill)
        Confirm.FORGET -> stringResource(R.string.btn_forget)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (action == Confirm.FORGET) {
                    Text(stringResource(R.string.confirm_forget_body, deviceName ?: stringResource(R.string.confirm_this_treadmill)))
                    if (moving) Spacer(Modifier.height(8.dp))
                }
                if (moving) Text(stringResource(R.string.confirm_moving_body))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (action == Confirm.FORGET) StrideColors.Stop else StrideColors.Text)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } },
        containerColor = StrideColors.SurfaceHigh,
        titleContentColor = StrideColors.Text,
        textContentColor = StrideColors.Text,
    )
}

/**
 * The big number: current belt speed, with the belt state and the target speed under it. While the pad counts down it
 * shows the 3-2-1 instead; PAUSED is a clear badge; and a STOPPED pad whose belt still moves says "Stopping".
 */
@Composable
private fun SpeedReadout(state: TreadmillManager.State) {
    val status = state.status
    if (status?.state == RunState.STARTING) {
        StartingCountdown(startedAtMs = state.startingSinceMs)
        return
    }
    val unit = state.unit
    val speedKmh = status?.speedKmh ?: 0.0
    val stopping = status?.isStopping == true
    val paused = status?.state == RunState.PAUSED
    val beltLabel = when {
        stopping -> stringResource(R.string.belt_stopping)
        else -> when (status?.state) {
            RunState.RUNNING -> stringResource(R.string.belt_running)
            RunState.STARTING -> stringResource(R.string.belt_starting)
            RunState.PAUSED -> stringResource(R.string.belt_paused)
            RunState.STOPPED -> stringResource(R.string.belt_stopped)
            null -> ""
        }
    }
    val beltColor = when {
        stopping || paused -> StrideColors.Warning
        status?.state == RunState.RUNNING -> StrideColors.Accent
        else -> StrideColors.Muted
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (paused) {
            PausedBadge(label = beltLabel)
        } else {
            Text(
                text = beltLabel.uppercase(),
                style = if (stopping) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                color = beltColor,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = Units.speedText(speedKmh, unit),
                style = MaterialTheme.typography.displayLarge,
                color = if (status == null || paused) StrideColors.Muted else StrideColors.Text,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = Units.speedUnitLabel(unit),
                style = MaterialTheme.typography.headlineSmall,
                color = StrideColors.Muted,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }
        // The target is worth showing while it differs from the belt: during a change, or before a start.
        val showTarget = status == null || status.state != RunState.RUNNING || abs(status.targetSpeedKmh - speedKmh) > 0.05
        Text(
            text = when {
                paused -> stringResource(R.string.paused_hint)
                showTarget -> stringResource(R.string.target_speed, Units.speedText(state.targetKmh, unit), Units.speedUnitLabel(unit))
                else -> " "
            },
            style = MaterialTheme.typography.bodyMedium,
            color = StrideColors.Muted,
        )
    }
}

/** A clear "PAUSED" pill, so a stopped-looking screen is never mistaken for a stopped belt. */
@Composable
private fun PausedBadge(label: String) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(StrideColors.WarningDim)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pause),
            contentDescription = null,
            tint = StrideColors.Warning,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.headlineSmall, color = StrideColors.Warning)
    }
}

/**
 * The pad is beeping and counting down before the belt moves (about three seconds): a big "Starting", a pulsing ring
 * and a 3-2-1 count. The count is paced by the clock since the pad reported STARTING; the screen changes by itself
 * when the pad reports RUNNING.
 */
@Composable
private fun StartingCountdown(startedAtMs: Long?) {
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            nowMs = SystemClock.elapsedRealtime()
            delay(100)
        }
    }
    val digit = StartCountdown.digitAt(if (startedAtMs == null) 0L else nowMs - startedAtMs)

    val pulse = rememberInfiniteTransition(label = "starting ring")
    val scale by pulse.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring scale",
    )
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring alpha",
    )
    val description = stringResource(R.string.starting_description, digit)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.belt_starting).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = StrideColors.Warning,
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val radius = (size.minDimension / 2f - stroke) * scale
                drawCircle(color = StrideColors.Warning.copy(alpha = alpha), radius = radius, style = Stroke(width = stroke))
            }
            Text(text = digit.toString(), style = MaterialTheme.typography.displayLarge, color = StrideColors.Text)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.starting_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = StrideColors.Muted,
        )
    }
}

@Composable
private fun StatsRow(state: TreadmillManager.State) {
    val status = state.status
    val unit = state.unit
    val steps = state.steps
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(
            label = stringResource(R.string.stat_time),
            value = Units.durationText(status?.durationSeconds ?: 0L),
            unit = null,
            modifier = Modifier.weight(1.15f),
        )
        StatTile(
            label = stringResource(R.string.stat_distance),
            value = Units.distanceText(status?.distanceKm ?: 0.0, unit),
            unit = Units.distanceUnitLabel(unit),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(if (state.estimatedKcal != null) R.string.stat_calories_estimated else R.string.stat_calories),
            value = (state.estimatedKcal ?: status?.kcal ?: 0).toString(),
            unit = "kcal",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = if (steps?.estimated == true) stringResource(R.string.stat_steps_estimated) else stringResource(R.string.stat_steps),
            value = (steps?.count ?: 0L).toString(),
            unit = null,
            modifier = Modifier.weight(1f),
        )
    }
}

/** SPD -, Start/Pause, SPD + in one row, then the wide STOP. */
@Composable
private fun ControlCluster(state: TreadmillManager.State, ready: Boolean, manager: TreadmillManager) {
    val status = state.status
    val running = status?.state == RunState.RUNNING
    val starting = status?.state == RunState.STARTING
    val connected = state.link == TreadmillManager.Link.CONNECTED
    val range = state.speedRange
    val atMin = state.targetKmh <= range.start + 0.001
    val atMax = state.targetKmh >= range.endInclusive - 0.001

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundTonalButton(
            label = stringResource(R.string.btn_speed_down),
            enabled = connected && running && !atMin,
            onClick = { manager.changeSpeed(-STEP_KMH) },
        )
        StartPauseButton(
            running = running,
            starting = starting,
            paused = status?.state == RunState.PAUSED,
            enabled = ready && !starting,
            onClick = manager::startOrPause,
        )
        RoundTonalButton(
            label = stringResource(R.string.btn_speed_up),
            enabled = connected && running && !atMax,
            onClick = { manager.changeSpeed(STEP_KMH) },
        )
    }
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = manager::stop,
        enabled = connected,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = StrideColors.Stop,
            contentColor = StrideColors.Text,
            disabledContainerColor = StrideColors.StopDim,
            disabledContentColor = StrideColors.Muted,
        ),
    ) {
        Text(stringResource(R.string.btn_stop), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun RoundTonalButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(84.dp),
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = StrideColors.SurfaceHigh,
            contentColor = StrideColors.Text,
            disabledContainerColor = StrideColors.Surface,
            disabledContentColor = StrideColors.Muted,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StartPauseButton(running: Boolean, starting: Boolean, paused: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = when {
        running -> stringResource(R.string.btn_pause)
        starting -> stringResource(R.string.belt_starting)
        paused -> stringResource(R.string.btn_resume)
        else -> stringResource(R.string.btn_start)
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(104.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (running) StrideColors.SurfaceHigh else StrideColors.Accent,
            contentColor = if (running) StrideColors.Text else StrideColors.OnAccent,
            disabledContainerColor = StrideColors.Surface,
            disabledContentColor = StrideColors.Muted,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(if (running) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Four quick speeds. The selected chip is the one matching the current target. */
@Composable
private fun PresetRow(state: TreadmillManager.State, enabled: Boolean, manager: TreadmillManager) {
    val unit = state.unit
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.presetsKmh.take(4).forEach { kmh ->
            val selected = abs(kmh - state.targetKmh) < 0.05
            FilterChip(
                selected = selected,
                enabled = enabled,
                onClick = { manager.selectSpeed(kmh) },
                label = {
                    Text(
                        text = Units.speedText(kmh, unit),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = StrideColors.Surface,
                    labelColor = StrideColors.Text,
                    selectedContainerColor = StrideColors.AccentDim,
                    selectedLabelColor = StrideColors.Text,
                    disabledContainerColor = StrideColors.Surface,
                    disabledLabelColor = StrideColors.Muted,
                ),
                border = null,
            )
        }
    }
}

@Composable
private fun BottomRow(unit: MeasureUnit, manager: TreadmillManager, onHistory: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnitToggle(unit = unit, onChange = manager::setUnit)
        Spacer(Modifier.weight(1f))
        StrideIconButton(iconRes = R.drawable.ic_history, contentDescription = stringResource(R.string.btn_history), onClick = onHistory)
        StrideIconButton(iconRes = R.drawable.ic_gear, contentDescription = stringResource(R.string.btn_settings), onClick = onSettings)
    }
}

/** mph | km as two small segments. */
@Composable
fun UnitToggle(unit: MeasureUnit, onChange: (MeasureUnit) -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(StrideColors.Surface),
    ) {
        UnitSegment(stringResource(R.string.unit_mph), unit == MeasureUnit.IMPERIAL) { onChange(MeasureUnit.IMPERIAL) }
        UnitSegment(stringResource(R.string.unit_km), unit == MeasureUnit.METRIC) { onChange(MeasureUnit.METRIC) }
    }
}

@Composable
private fun UnitSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) StrideColors.AccentDim else Color.Transparent,
            contentColor = if (selected) StrideColors.Text else StrideColors.Muted,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
