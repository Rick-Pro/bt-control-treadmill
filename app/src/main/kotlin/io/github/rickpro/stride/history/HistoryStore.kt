package io.github.rickpro.stride.history

import android.content.Context
import io.github.rickpro.stride.core.history.Workout
import io.github.rickpro.stride.core.history.WorkoutJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every finished walk, one [WorkoutJson] line per walk in `filesDir/workouts.jsonl`. Nothing else stores it and
 * nothing leaves the phone unless the user taps Share. Loaded once at start; every change rewrites the file on
 * the IO dispatcher.
 */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _workouts = MutableStateFlow<List<Workout>>(emptyList())

    /** Newest first. */
    val workouts: StateFlow<List<Workout>> = _workouts.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            lock.withLock {
                val list = if (file.isFile) file.readLines().mapNotNull(WorkoutJson::decode) else emptyList()
                _workouts.value = list.sortedByDescending { it.startedAtEpochMs }
                _loaded.value = true
            }
        }
    }

    fun add(workout: Workout) {
        scope.launch {
            lock.withLock {
                _workouts.value = (listOf(workout) + _workouts.value.filterNot { it.id == workout.id })
                    .sortedByDescending { it.startedAtEpochMs }
                file.appendText(WorkoutJson.encode(workout) + "\n")
            }
        }
    }

    fun delete(id: String) {
        scope.launch {
            lock.withLock {
                val kept = _workouts.value.filterNot { it.id == id }
                if (kept.size == _workouts.value.size) return@withLock
                _workouts.value = kept
                rewrite(kept)
            }
        }
    }

    /** All walks as CSV, oldest first, for Share. Times are local. */
    fun exportCsv(): String {
        val zone = ZoneId.systemDefault()
        val day = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        val rows = _workouts.value.sortedBy { it.startedAtEpochMs }.map { w ->
            val at = Instant.ofEpochMilli(w.startedAtEpochMs).atZone(zone)
            listOf(
                at.format(day), at.format(time), w.durationSeconds.toString(),
                "%.3f".format(Locale.ROOT, w.distanceKm), w.kcal.toString(), w.steps.toString(),
                "%.2f".format(Locale.ROOT, w.avgSpeedKmh), "%.2f".format(Locale.ROOT, w.maxSpeedKmh),
            ).joinToString(",")
        }
        return (listOf(CSV_HEADER) + rows).joinToString("\n") + "\n"
    }

    private fun rewrite(list: List<Workout>) {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(list.sortedBy { it.startedAtEpochMs }.joinToString("") { WorkoutJson.encode(it) + "\n" })
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private companion object {
        const val FILE_NAME = "workouts.jsonl"
        const val CSV_HEADER = "date,start,duration_s,distance_km,kcal,steps,avg_kmh,max_kmh"
    }
}
