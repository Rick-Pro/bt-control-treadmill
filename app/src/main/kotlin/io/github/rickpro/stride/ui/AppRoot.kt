package io.github.rickpro.stride.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rickpro.stride.R
import io.github.rickpro.stride.StrideApp
import io.github.rickpro.stride.ble.BlePermissions
import io.github.rickpro.stride.session.TreadmillManager
import io.github.rickpro.stride.ui.theme.StrideColors

/** Where the user is. There is no navigation library: one screen plus a short stack of secondary ones. */
sealed class Screen {
    data object Control : Screen()
    data object History : Screen()
    data class Detail(val workoutId: String) : Screen()
}

/**
 * Wires the app's state to the screens. Setup shows whenever no treadmill is remembered, the main
 * screen otherwise, with History and a walk's detail reachable from it. Alerts and snackbars are global.
 */
@Composable
fun AppRoot(app: StrideApp, keepScreenOn: (Boolean) -> Unit) {
    val context = LocalContext.current
    val manager = app.manager
    val state by manager.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.Control) }
    var settingsOpen by remember { mutableStateOf(false) }
    // "Find a treadmill" from the Control screen: the link is closed and Setup scans again.
    var finding by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(BlePermissions.granted(context)) }
    var keepOnSetting by remember { mutableStateOf(app.settings.keepScreenOn) }
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionGranted = BlePermissions.granted(context)
        if (permissionGranted) manager.onForeground()
    }
    val requestPermission = { permissionLauncher.launch(BlePermissions.required) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionGranted = BlePermissions.granted(context)
    }

    // Screen stays on only while the belt moves, if the setting allows it.
    val moving = state.status?.isMoving == true
    LaunchedEffect(moving, keepOnSetting) { keepScreenOn(moving && keepOnSetting) }

    val msgNotConnected = stringResource(R.string.msg_not_connected)
    val msgNoData = stringResource(R.string.msg_no_data)
    val msgReconnected = stringResource(R.string.msg_reconnected)
    val msgIncompatible = stringResource(R.string.alert_incompatible)
    LaunchedEffect(manager) {
        manager.messages.collect { message ->
            val text = when (message) {
                TreadmillManager.Message.NOT_CONNECTED -> msgNotConnected
                TreadmillManager.Message.NO_DATA -> msgNoData
                TreadmillManager.Message.RECONNECTED -> msgReconnected
                TreadmillManager.Message.INCOMPATIBLE -> msgIncompatible
            }
            snackbar.showSnackbar(text)
        }
    }

    // Leaving Find without picking anything: take the remembered treadmill back.
    val cancelFind = {
        finding = false
        manager.reconnect()
    }

    BackHandler(enabled = screen != Screen.Control || finding) {
        if (finding) {
            cancelFind()
        } else {
            screen = when (screen) {
                is Screen.Detail -> Screen.History
                else -> Screen.Control
            }
        }
    }

    Scaffold(
        containerColor = StrideColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = screen) {
                Screen.Control ->
                    if (state.link == TreadmillManager.Link.NO_DEVICE || finding) {
                        SetupScreen(
                            manager = manager,
                            bluetoothOn = state.bluetoothOn,
                            permissionGranted = permissionGranted,
                            onRequestPermission = requestPermission,
                            autoScan = finding,
                            onPicked = { finding = false },
                            onCancel = if (finding && state.link != TreadmillManager.Link.NO_DEVICE) cancelFind else null,
                        )
                    } else {
                        ControlScreen(
                            state = state,
                            manager = manager,
                            permissionGranted = permissionGranted,
                            onRequestPermission = requestPermission,
                            onHistory = { screen = Screen.History },
                            onSettings = { settingsOpen = true },
                            onFind = { finding = true },
                        )
                    }
                Screen.History ->
                    HistoryScreen(
                        history = app.history,
                        unit = state.unit,
                        onBack = { screen = Screen.Control },
                        onOpen = { id -> screen = Screen.Detail(id) },
                    )
                is Screen.Detail ->
                    WorkoutDetailScreen(
                        history = app.history,
                        workoutId = current.workoutId,
                        unit = state.unit,
                        onBack = { screen = Screen.History },
                        onDeleted = { screen = Screen.History },
                    )
            }
        }
    }

    if (settingsOpen) {
        SettingsSheet(
            app = app,
            state = state,
            onKeepScreenOnChanged = { keepOnSetting = it },
            onDismiss = { settingsOpen = false },
        )
    }

    state.alert?.let { alert -> AlertDialogFor(alert, onDismiss = manager::dismissAlert) }
}

@Composable
private fun AlertDialogFor(alert: TreadmillManager.Alert, onDismiss: () -> Unit) {
    val (title, body) = when (alert) {
        TreadmillManager.Alert.LINK_LOST_WHILE_MOVING ->
            stringResource(R.string.alert_lost_moving_title) to stringResource(R.string.alert_lost_moving_body)
        TreadmillManager.Alert.STOP_NOT_CONFIRMED ->
            stringResource(R.string.alert_stop_title) to stringResource(R.string.alert_stop_body)
        TreadmillManager.Alert.LINK_LOST -> return // shown as a banner on the main screen, not a dialog
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) } },
        title = { Text(title) },
        text = { Text(body) },
        containerColor = StrideColors.SurfaceHigh,
        titleContentColor = StrideColors.Text,
        textContentColor = StrideColors.Text,
    )
}
