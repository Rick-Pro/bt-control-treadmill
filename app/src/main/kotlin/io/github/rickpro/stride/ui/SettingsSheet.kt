package io.github.rickpro.stride.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rickpro.stride.R
import io.github.rickpro.stride.StrideApp
import io.github.rickpro.stride.core.units.MeasureUnit
import io.github.rickpro.stride.core.units.Units
import io.github.rickpro.stride.session.TreadmillManager
import io.github.rickpro.stride.ui.components.shareText
import io.github.rickpro.stride.ui.theme.StrideColors

/**
 * The settings sheet: units, keep screen on, the treadmill beeper (experimental), four presets, forget the
 * treadmill, the diagnostics log and About. Everything applies at once except the presets, which have Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    app: StrideApp,
    state: TreadmillManager.State,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val manager = app.manager
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var keepOn by remember { mutableStateOf(app.settings.keepScreenOn) }
    var beeper by remember { mutableStateOf(true) }
    var logOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    val version = app.versionName

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = StrideColors.Surface,
        contentColor = StrideColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            SettingRow(title = stringResource(R.string.settings_units)) {
                UnitToggle(unit = state.unit, onChange = manager::setUnit)
            }
            Divider()

            SwitchRow(
                title = stringResource(R.string.settings_keep_screen_on),
                checked = keepOn,
                onChange = {
                    keepOn = it
                    app.settings.keepScreenOn = it
                    onKeepScreenOnChanged(it)
                },
            )
            Divider()

            SwitchRow(
                title = stringResource(R.string.settings_beeper),
                body = stringResource(R.string.settings_beeper_body),
                checked = beeper,
                enabled = state.link == TreadmillManager.Link.CONNECTED,
                onChange = {
                    beeper = it
                    manager.setBeeper(it)
                },
            )
            Divider()

            PresetsEditor(state = state, onSave = manager::setPresets)
            Divider()

            Text(stringResource(R.string.settings_treadmill), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.settings_forget_body, state.deviceName ?: "this treadmill"),
                style = MaterialTheme.typography.bodyMedium,
                color = StrideColors.Muted,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    manager.forget()
                    onDismiss()
                },
                enabled = state.link != TreadmillManager.Link.NO_DEVICE,
            ) {
                Text(stringResource(R.string.btn_forget))
            }
            Divider()

            Text(stringResource(R.string.settings_diagnostics), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_diagnostics_body), style = MaterialTheme.typography.bodyMedium, color = StrideColors.Muted)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { logOpen = true }) { Text(stringResource(R.string.settings_diagnostics)) }
            Divider()

            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.settings_about_body, version),
                style = MaterialTheme.typography.bodyMedium,
                color = StrideColors.Muted,
            )
        }
    }

    if (logOpen) {
        DiagnosticsDialog(manager = manager, onShare = { shareText(context, "Stride diagnostics", manager.diagnosticsText()) }, onDismiss = { logOpen = false })
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = StrideColors.Outline)
}

@Composable
private fun SettingRow(title: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, body: String? = null, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = StrideColors.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StrideColors.OnAccent,
                checkedTrackColor = StrideColors.Accent,
                uncheckedThumbColor = StrideColors.Muted,
                uncheckedTrackColor = StrideColors.SurfaceHigh,
            ),
        )
    }
}

/** Four text fields in the chosen unit; Save converts back to km/h and stores them. */
@Composable
private fun PresetsEditor(state: TreadmillManager.State, onSave: (List<Double>) -> Unit) {
    val unit = state.unit
    val unitLabel = Units.speedUnitLabel(unit)
    val initial = remember(state.presetsKmh, unit) { state.presetsKmh.take(4).map { Units.speedText(it, unit) } }
    var fields by remember(initial) { mutableStateOf(initial) }
    val parsed = fields.map { it.trim().replace(',', '.').toDoubleOrNull() }
    val valid = parsed.size == 4 && parsed.all { it != null && it > 0 }
    val dirty = fields != initial

    Text(stringResource(R.string.settings_presets), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.settings_presets_body, unitLabel), style = MaterialTheme.typography.bodyMedium, color = StrideColors.Muted)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.forEachIndexed { i, value ->
            OutlinedTextField(
                value = value,
                onValueChange = { new -> fields = fields.toMutableList().also { it[i] = new } },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleMedium,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = {
            val kmh = parsed.map { v -> if (unit == MeasureUnit.IMPERIAL) Units.mphToKmh(v!!) else v!! }
            onSave(kmh)
        },
        enabled = valid && dirty,
    ) {
        Text(stringResource(R.string.settings_save))
    }
}

@Composable
private fun DiagnosticsDialog(manager: TreadmillManager, onShare: () -> Unit, onDismiss: () -> Unit) {
    val revision by manager.log.revision.collectAsStateWithLifecycle()
    val lines = remember(revision) { manager.log.snapshot() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_diagnostics)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (lines.isEmpty()) {
                    Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium, color = StrideColors.Muted)
                }
                lines.takeLast(300).forEach { line ->
                    Text(line, style = MaterialTheme.typography.labelSmall, color = StrideColors.Muted)
                }
            }
        },
        confirmButton = { TextButton(onClick = onShare) { Text(stringResource(R.string.btn_share)) } },
        dismissButton = {
            Row {
                TextButton(onClick = manager.log::clear) { Text(stringResource(R.string.btn_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) }
            }
        },
        containerColor = StrideColors.SurfaceHigh,
        titleContentColor = StrideColors.Text,
        textContentColor = StrideColors.Text,
    )
}
