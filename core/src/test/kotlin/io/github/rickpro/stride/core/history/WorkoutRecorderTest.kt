package io.github.rickpro.stride.core.history

import io.github.rickpro.stride.core.control.StepEstimator
import io.github.rickpro.stride.core.protocol.RunState
import io.github.rickpro.stride.core.protocol.TreadmillStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRecorderTest {
    private val t0 = 1_700_000_000_000L
    private val steps = StepEstimator.Steps(100, estimated = true)

    private fun status(state: RunState, seconds: Long, km: Double, speed: Double = 2.0, kcal: Int = 10) =
        TreadmillStatus(state, speed, speed, km, seconds, steps = 0, kcal = kcal, maxSpeedKmh = 6.0)

    /** Feeds one status per second from [from] to [to] seconds of belt time. */
    private fun WorkoutRecorder.walk(from: Long, to: Long, speed: Double = 2.0): Workout? {
        var out: Workout? = null
        for (s in from..to) out = update(status(RunState.RUNNING, s, s * speed / 3600, speed), steps, t0 + s * 1000) ?: out
        return out
    }

    @Test
    fun `a walk opens on running and ends after two stopped statuses`() {
        val recorder = WorkoutRecorder()
        assertFalse(recorder.isRecording)
        assertNull(recorder.update(status(RunState.STOPPED, 0, 0.0), steps, t0))
        assertFalse(recorder.isRecording)
        recorder.walk(0, 120)
        assertTrue(recorder.isRecording)
        assertNull(recorder.update(status(RunState.STOPPED, 120, 0.0667), steps, t0 + 121_000))
        val workout = recorder.update(status(RunState.STOPPED, 120, 0.0667), steps, t0 + 122_000)
        assertNotNull(workout)
        assertFalse(recorder.isRecording)
        assertEquals(120L, workout!!.durationSeconds)
        assertEquals(0.0667, workout.distanceKm, 1e-9)
        assertEquals(100L, workout.steps)
        assertTrue(workout.stepsEstimated)
        assertEquals(10, workout.kcal)
        assertEquals(2.0, workout.avgSpeedKmh, 0.01)
        assertEquals(2.0, workout.maxSpeedKmh, 1e-9)
        assertEquals(t0, workout.startedAtEpochMs)
        assertEquals(t0 + 122_000, workout.endedAtEpochMs)
        assertEquals(Workout.idFor(t0), workout.id)
    }

    @Test
    fun `pause keeps the walk open`() {
        val recorder = WorkoutRecorder()
        recorder.walk(0, 60)
        repeat(5) { assertNull(recorder.update(status(RunState.PAUSED, 60, 60 * 2.0 / 3600, 0.0), steps, t0 + 61_000 + it * 1000L)) }
        assertTrue(recorder.isRecording)
        recorder.walk(61, 90)
        assertTrue(recorder.isRecording)
        assertEquals(2.0, recorder.flush(t0 + 100_000)!!.maxSpeedKmh, 1e-9)
    }

    @Test
    fun `walks under 30 seconds or without distance are dropped`() {
        val recorder = WorkoutRecorder()
        recorder.walk(0, 20)
        assertNull(recorder.flush(t0 + 21_000))
        assertFalse(recorder.isRecording)
        for (s in 0L..60L) recorder.update(status(RunState.RUNNING, s, 0.0), steps, t0 + s * 1000)
        assertNull(recorder.flush(t0 + 61_000))
    }

    @Test
    fun `a counter reset ends the walk with the last numbers and opens a new one`() {
        val recorder = WorkoutRecorder()
        recorder.walk(0, 100)
        val finished = recorder.update(status(RunState.RUNNING, 0, 0.0), steps, t0 + 101_000)
        assertNotNull(finished)
        assertEquals(100L, finished!!.durationSeconds)
        assertTrue(recorder.isRecording)
        recorder.walk(1, 50)
        val second = recorder.flush(t0 + 200_000)
        assertEquals(50L, second!!.durationSeconds)
        assertEquals(Workout.idFor(t0 + 101_000), second.id)
    }

    @Test
    fun `samples are taken every 5 seconds of belt time and the max speed is tracked`() {
        val recorder = WorkoutRecorder()
        recorder.walk(0, 23, speed = 2.0)
        recorder.walk(24, 40, speed = 3.5)
        val workout = recorder.flush(t0 + 41_000)!!
        assertEquals(listOf<Long>(0, 5, 10, 15, 20, 25, 30, 35, 40), workout.samples.map { it.atSeconds })
        assertEquals(3.5, workout.samples.last().kmh, 1e-9)
        assertEquals(3.5, workout.maxSpeedKmh, 1e-9)
    }

    @Test
    fun `connecting mid walk back-dates the start`() {
        val recorder = WorkoutRecorder()
        recorder.update(status(RunState.RUNNING, 600, 0.3), steps, t0)
        val workout = recorder.flush(t0 + 1000)!!
        assertEquals(t0 - 600_000, workout.startedAtEpochMs)
    }

    @Test
    fun `a link that stays down ends the walk, a short drop does not`() {
        val recorder = WorkoutRecorder()
        recorder.walk(0, 60)
        assertNull(recorder.onLinkDown(t0 + 61_000))
        assertTrue(recorder.isRecording)
        // Data returns 10 s later: same walk.
        assertNull(recorder.update(status(RunState.RUNNING, 70, 0.04), steps, t0 + 71_000))
        assertTrue(recorder.isRecording)
        // Data returns 2 minutes later: the old walk ends, a new one starts from the new status.
        val old = recorder.update(status(RunState.RUNNING, 5, 0.003), steps, t0 + 200_000)
        assertNotNull(old)
        assertEquals(70L, old!!.durationSeconds)
        assertTrue(recorder.isRecording)
        // Down for good: the new walk is closed and dropped (5 s is too short), nothing is recording.
        assertNull(recorder.onLinkDown(t0 + 300_000))
        assertFalse(recorder.isRecording)
    }
}
