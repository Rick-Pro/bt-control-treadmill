package io.github.rickpro.stride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rickpro.stride.R
import io.github.rickpro.stride.ble.BleScanner
import io.github.rickpro.stride.session.TreadmillManager
import io.github.rickpro.stride.ui.components.openBluetoothSettings
import io.github.rickpro.stride.ui.theme.StrideColors

private const val SCAN_MS = 15_000L

/**
 * First run, or after Forget: one card. Turn on the treadmill, tap Find, tap the treadmill (or let Stride
 * pick it when it is the only one found). From then on the app connects by itself.
 */
@Composable
fun SetupScreen(
    manager: TreadmillManager,
    bluetoothOn: Boolean,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    val scanner = remember { BleScanner(context) }
    val results by scanner.results.collectAsStateWithLifecycle()
    val scanning by scanner.scanning.collectAsStateWithLifecycle()
    val error by scanner.error.collectAsStateWithLifecycle()
    var scanned by remember { mutableStateOf(false) }
    var connectingTo by remember { mutableStateOf<String?>(null) }

    DisposableEffect(scanner) { onDispose { scanner.stop() } }

    fun pick(found: BleScanner.Found) {
        if (connectingTo != null) return
        connectingTo = found.address
        scanner.stop()
        manager.connect(found.address, found.name)
    }

    // Exactly one treadmill after a full scan: no need to ask.
    LaunchedEffect(scanning, results.size) {
        if (scanned && !scanning && results.size == 1 && connectingTo == null) pick(results.first())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.Unspecified, // keep the icon's own colors
            modifier = Modifier.size(132.dp),
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = StrideColors.Text)
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(StrideColors.Surface)
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge, color = StrideColors.Text)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyLarge, color = StrideColors.Muted)
            Spacer(Modifier.height(20.dp))

            when {
                !permissionGranted -> PrimaryButton(stringResource(R.string.btn_allow), onClick = onRequestPermission)
                !bluetoothOn -> PrimaryButton(stringResource(R.string.btn_open_bluetooth), onClick = { openBluetoothSettings(context) })
                scanning -> ScanningRow()
                else -> PrimaryButton(
                    label = if (scanned) stringResource(R.string.btn_find_again) else stringResource(R.string.btn_find),
                    onClick = {
                        scanned = true
                        scanner.start(SCAN_MS)
                    },
                )
            }

            error?.let { code ->
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.setup_scan_failed, code), style = MaterialTheme.typography.bodyMedium, color = StrideColors.Warning)
            }

            if (results.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (connectingTo != null) stringResource(R.string.setup_found_one) else stringResource(R.string.setup_pick),
                    style = MaterialTheme.typography.labelMedium,
                    color = StrideColors.Muted,
                )
                Spacer(Modifier.height(8.dp))
                results.forEach { found -> FoundRow(found = found, connecting = found.address == connectingTo, onClick = { pick(found) }) }
            } else if (scanned && !scanning && error == null) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.setup_none), style = MaterialTheme.typography.bodyMedium, color = StrideColors.Muted)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = StrideColors.Accent, contentColor = StrideColors.OnAccent),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ScanningRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = StrideColors.Accent, strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.setup_scanning), style = MaterialTheme.typography.bodyLarge, color = StrideColors.Text)
    }
}

@Composable
private fun FoundRow(found: BleScanner.Found, connecting: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = StrideColors.Text),
    ) {
        Column(Modifier.weight(1f)) {
            Text(found.name ?: found.address, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.setup_signal, found.rssi), style = MaterialTheme.typography.labelSmall, color = StrideColors.Muted)
        }
        if (connecting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = StrideColors.Accent, strokeWidth = 2.dp)
    }
}
