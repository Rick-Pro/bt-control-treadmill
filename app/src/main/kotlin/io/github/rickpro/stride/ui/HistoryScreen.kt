package io.github.rickpro.stride.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rickpro.stride.R
import io.github.rickpro.stride.core.history.HistoryStats
import io.github.rickpro.stride.core.history.Workout
import io.github.rickpro.stride.core.units.MeasureUnit
import io.github.rickpro.stride.core.units.Units
import io.github.rickpro.stride.history.HistoryStore
import io.github.rickpro.stride.ui.components.StrideIconButton
import io.github.rickpro.stride.ui.components.StrideTopBar
import io.github.rickpro.stride.ui.components.shareText
import io.github.rickpro.stride.ui.theme.NumberStyle
import io.github.rickpro.stride.ui.theme.StrideColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Every walk, newest first, grouped by day, with totals on top. Tap opens the detail; long press deletes. */
@Composable
fun HistoryScreen(history: HistoryStore, unit: MeasureUnit, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val workouts by history.workouts.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Workout?>(null) }
    val zone = remember { ZoneId.systemDefault() }

    Column(Modifier.fillMaxSize()) {
        StrideTopBar(title = stringResource(R.string.history_title), onBack = onBack) {
            StrideIconButton(
                iconRes = R.drawable.ic_share,
                contentDescription = stringResource(R.string.btn_share),
                enabled = workouts.isNotEmpty(),
                onClick = { shareText(context, context.getString(R.string.history_share_subject), history.exportCsv()) },
            )
        }

        if (workouts.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = StrideColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            )
            return@Column
        }

        val now = System.currentTimeMillis()
        val offset = zone.rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds
        val week = remember(workouts, now) { HistoryStats.thisWeek(workouts, now, offset) }
        val all = remember(workouts) { HistoryStats.totals(workouts) }
        val grouped = remember(workouts) {
            workouts.groupBy { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() }
                .toList()
                .sortedByDescending { it.first }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TotalsCard(
                        title = stringResource(R.string.history_this_week),
                        walks = week.walks, seconds = week.seconds, km = week.km, kcal = week.kcal, unit = unit,
                        modifier = Modifier.weight(1f),
                    )
                    TotalsCard(
                        title = stringResource(R.string.history_all_time),
                        walks = all.walks, seconds = all.seconds, km = all.km, kcal = all.kcal, unit = unit,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(R.string.history_longpress_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = StrideColors.Muted,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            grouped.forEach { (day, walks) ->
                item(key = "day-$day") {
                    Text(
                        text = dayLabel(day),
                        style = MaterialTheme.typography.labelMedium,
                        color = StrideColors.Muted,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp, bottom = 2.dp),
                    )
                }
                items(walks, key = { it.id }) { walk ->
                    WalkRow(walk = walk, unit = unit, zone = zone, onClick = { onOpen(walk.id) }, onLongClick = { toDelete = walk })
                }
            }
        }
    }

    toDelete?.let { walk ->
        DeleteDialog(onConfirm = { history.delete(walk.id); toDelete = null }, onDismiss = { toDelete = null })
    }
}

@Composable
private fun TotalsCard(title: String, walks: Int, seconds: Long, km: Double, kcal: Int, unit: MeasureUnit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(StrideColors.Surface)
            .padding(14.dp),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = StrideColors.Muted)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${Units.distanceText(km, unit)} ${Units.distanceUnitLabel(unit)}",
            style = NumberStyle.copy(fontSize = 24.sp, lineHeight = 28.sp, textAlign = TextAlign.Start),
            color = StrideColors.Text,
        )
        Text(
            text = "${Units.durationText(seconds)} · $kcal kcal",
            style = MaterialTheme.typography.bodyMedium,
            color = StrideColors.Muted,
        )
        Text(
            text = if (walks == 1) stringResource(R.string.history_walk, walks) else stringResource(R.string.history_walks, walks),
            style = MaterialTheme.typography.bodyMedium,
            color = StrideColors.Muted,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WalkRow(walk: Workout, unit: MeasureUnit, zone: ZoneId, onClick: () -> Unit, onLongClick: () -> Unit) {
    val time = remember(walk.startedAtEpochMs) {
        Instant.ofEpochMilli(walk.startedAtEpochMs).atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(StrideColors.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "${Units.distanceText(walk.distanceKm, unit)} ${Units.distanceUnitLabel(unit)}",
                style = MaterialTheme.typography.titleMedium,
                color = StrideColors.Text,
            )
            Text(
                text = "$time · ${Units.durationText(walk.durationSeconds)} · ${Units.speedText(walk.avgSpeedKmh, unit)} ${Units.speedUnitLabel(unit)}",
                style = MaterialTheme.typography.bodyMedium,
                color = StrideColors.Muted,
            )
        }
        Text(
            text = "${walk.kcal} kcal",
            style = MaterialTheme.typography.bodyMedium,
            color = StrideColors.Muted,
        )
    }
}

@Composable
fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_delete_title)) },
        text = { Text(stringResource(R.string.history_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.btn_delete), color = StrideColors.Stop) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } },
        containerColor = StrideColors.SurfaceHigh,
        titleContentColor = StrideColors.Text,
        textContentColor = StrideColors.Text,
    )
}

/** "Today", "Yesterday", or the date. */
@Composable
fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}
