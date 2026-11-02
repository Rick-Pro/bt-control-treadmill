package io.github.rickpro.stride.core.history

import io.github.rickpro.stride.core.control.StepEstimator
import io.github.rickpro.stride.core.protocol.RunState
import io.github.rickpro.stride.core.protocol.TreadmillStatus
import kotlin.math.max

/**
 * Watches the stream of treadmill statuses and cuts it into walks.
 *
 * - A walk opens when the belt moves (RUNNING or STARTING) and no walk is open.
 * - PAUSED keeps the walk open. STOPPED for two statuses in a row ends it. The treadmill's own
 *   counters resetting (duration going backwards) ends the walk with the last numbers seen, and a new
 *   one opens if the belt is moving again.
 * - If the link was down for more than [STALE_MS], the old walk ends when data returns, or on [flush].
 * - Walks shorter than [MIN_SECONDS] or with no distance are dropped.
 *
 * Pure logic: the caller passes the wall clock in epoch milliseconds.
 */
class WorkoutRecorder {
    private var startedAtEpochMs = 0L
    private var lastStatus: TreadmillStatus? = null
    private var lastSteps: StepEstimator.Steps? = null
    private var lastEstimatedKcal: Int? = null
    private var lastDataAtEpochMs = 0L
    private var maxSpeedKmh = 0.0
    private var samples = mutableListOf<SpeedSample>()
    private var nextSampleAtS = 0L
    private var stoppedInARow = 0

    /** True while a walk is being recorded. */
    val isRecording: Boolean get() = lastStatus != null

    /** Feed every decoded status. Returns a finished walk when one ends, null otherwise. */
    fun update(status: TreadmillStatus, steps: StepEstimator.Steps, nowEpochMs: Long, estimatedKcal: Int? = null): Workout? {
        var finished: Workout? = null
        if (isRecording) {
            val previous = lastStatus!!
            val stale = nowEpochMs - lastDataAtEpochMs > STALE_MS
            val counterReset = status.durationSeconds < previous.durationSeconds || status.distanceKm < previous.distanceKm
            if (stale || counterReset) {
                finished = close(nowEpochMs)
            }
        }
        if (!isRecording) {
            if (status.isMoving) open(status, steps, nowEpochMs, estimatedKcal)
            return finished
        }
        lastStatus = status
        lastSteps = steps
        lastEstimatedKcal = estimatedKcal
        lastDataAtEpochMs = nowEpochMs
        maxSpeedKmh = max(maxSpeedKmh, status.speedKmh)
        if (status.durationSeconds >= nextSampleAtS && samples.size < Workout.MAX_SAMPLES) {
            samples += SpeedSample(status.durationSeconds, status.speedKmh)
            nextSampleAtS = status.durationSeconds - status.durationSeconds % Workout.SAMPLE_EVERY_SECONDS + Workout.SAMPLE_EVERY_SECONDS
        }
        if (status.state == RunState.STOPPED) {
            stoppedInARow++
            if (stoppedInARow >= STOPPED_STATUSES_TO_END) return close(nowEpochMs) ?: finished
        } else {
            stoppedInARow = 0
        }
        return finished
    }

    /**
     * The link went down. The belt may still be running, so the walk stays open for a reconnection;
     * it only ends here when no data has arrived for [STALE_MS].
     */
    fun onLinkDown(nowEpochMs: Long): Workout? =
        if (isRecording && nowEpochMs - lastDataAtEpochMs > STALE_MS) close(nowEpochMs) else null

    /** Ends the open walk now, whatever its state (app closing, user forgot the treadmill). */
    fun flush(nowEpochMs: Long): Workout? = if (isRecording) close(nowEpochMs) else null

    private fun open(status: TreadmillStatus, steps: StepEstimator.Steps, nowEpochMs: Long, estimatedKcal: Int?) {
        startedAtEpochMs = nowEpochMs - status.durationSeconds * 1000 // connected mid-walk: back-date the start
        lastStatus = status
        lastSteps = steps
        lastEstimatedKcal = estimatedKcal
        lastDataAtEpochMs = nowEpochMs
        maxSpeedKmh = status.speedKmh
        samples = mutableListOf(SpeedSample(status.durationSeconds, status.speedKmh))
        nextSampleAtS = status.durationSeconds - status.durationSeconds % Workout.SAMPLE_EVERY_SECONDS + Workout.SAMPLE_EVERY_SECONDS
        stoppedInARow = 0
    }

    private fun close(nowEpochMs: Long): Workout? {
        val status = lastStatus ?: return null
        val steps = lastSteps
        val workout = Workout(
            id = Workout.idFor(startedAtEpochMs),
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = max(nowEpochMs, startedAtEpochMs),
            durationSeconds = status.durationSeconds,
            distanceKm = status.distanceKm,
            kcal = status.kcal,
            steps = steps?.count ?: status.steps,
            stepsEstimated = steps?.estimated ?: false,
            avgSpeedKmh = if (status.durationSeconds > 0) status.distanceKm / (status.durationSeconds / 3600.0) else 0.0,
            maxSpeedKmh = maxSpeedKmh,
            samples = samples.toList(),
            estimatedKcal = lastEstimatedKcal,
        )
        lastStatus = null
        lastSteps = null
        lastEstimatedKcal = null
        samples = mutableListOf()
        stoppedInARow = 0
        maxSpeedKmh = 0.0
        return if (workout.durationSeconds < MIN_SECONDS || workout.distanceKm <= 0.0) null else workout
    }

    companion object {
        const val MIN_SECONDS = 30L
        const val STALE_MS = 60_000L
        const val STOPPED_STATUSES_TO_END = 2
    }
}
