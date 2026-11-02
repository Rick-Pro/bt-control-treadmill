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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rickpro.stride.R
import io.github.rickpro.stride.core.units.MeasureUnit
import io.github.rickpro.stride.core.units.Units
import io.github.rickpro.stride.history.HistoryStore
import io.github.rickpro.stride.ui.components.SpeedChart
import io.github.rickpro.stride.ui.components.StatTile
import io.github.rickpro.stride.ui.components.StrideIconButton
import io.github.rickpro.stride.ui.components.StrideTopBar
import io.github.rickpro.stride.ui.theme.StrideColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** All the numbers of one walk and its speed chart. Delete lives in the top bar, behind a confirm. */
@Composable
fun WorkoutDetailScreen(history: HistoryStore, workoutId: String, unit: MeasureUnit, onBack: () -> Unit, onDeleted: () -> Unit) {
    val workouts by history.workouts.collectAsStateWithLifecycle()
    val walk = workouts.firstOrNull { it.id == workoutId }
    var confirmDelete by remember { mutableStateOf(false) }

    // Deleted from somewhere else, or not there any more: go back.
    LaunchedEffect(walk) { if (walk == null) onBack() }
    if (walk == null) return

    val started = remember(walk.startedAtEpochMs) {
        Instant.ofEpochMilli(walk.startedAtEpochMs).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    }

    Column(Modifier.fillMaxSize()) {
        StrideTopBar(title = stringResource(R.string.detail_title), onBack = onBack) {
            StrideIconButton(iconRes = R.drawable.ic_delete, contentDescription = stringResource(R.string.btn_delete), onClick = { confirmDelete = true })
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(started, style = MaterialTheme.typography.bodyLarge, color = StrideColors.Muted)
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.stat_time), Units.durationText(walk.durationSeconds), null, Modifier.weight(1.15f))
                StatTile(stringResource(R.string.stat_distance), Units.distanceText(walk.distanceKm, unit), Units.distanceUnitLabel(unit), Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_calories), walk.kcal.toString(), "kcal", Modifier.weight(1f))
                StatTile(
                    if (walk.stepsEstimated) stringResource(R.string.stat_steps_estimated) else stringResource(R.string.stat_steps),
                    walk.steps.toString(), null, Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.detail_avg_speed), Units.speedText(walk.avgSpeedKmh, unit), Units.speedUnitLabel(unit), Modifier.weight(1f))
                StatTile(stringResource(R.string.detail_max_speed), Units.speedText(walk.maxSpeedKmh, unit), Units.speedUnitLabel(unit), Modifier.weight(1f))
                StatTile(stringResource(R.string.detail_pace), Units.paceText(walk.avgSpeedKmh, unit), "/" + Units.distanceUnitLabel(unit), Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(StrideColors.Surface)
                    .padding(14.dp),
            ) {
                Text(stringResource(R.string.detail_speed_chart).uppercase(), style = MaterialTheme.typography.labelMedium, color = StrideColors.Muted)
                Spacer(Modifier.height(10.dp))
                if (walk.samples.size >= 2) {
                    SpeedChart(samples = walk.samples, unit = unit)
                } else {
                    Text(stringResource(R.string.detail_no_samples), style = MaterialTheme.typography.bodyMedium, color = StrideColors.Muted)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        DeleteDialog(
            onConfirm = {
                confirmDelete = false
                history.delete(walk.id)
                onDeleted()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
