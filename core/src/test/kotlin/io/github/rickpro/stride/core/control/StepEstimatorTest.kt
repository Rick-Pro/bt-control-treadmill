package io.github.rickpro.stride.core.control

import io.github.rickpro.stride.core.protocol.RunState
import io.github.rickpro.stride.core.protocol.TreadmillStatus
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class StepEstimatorTest {
    private fun status(distanceKm: Double, durationS: Long, steps: Long = 0, speed: Double = 4.0) = TreadmillStatus(
        state = RunState.RUNNING, speedKmh = speed, targetSpeedKmh = speed, distanceKm = distanceKm,
        durationSeconds = durationS, steps = steps, kcal = 0, maxSpeedKmh = 6.0,
    )

    @Test
    fun `uses the treadmill count once it reports steps`() {
        val estimator = StepEstimator()
        assertEquals(StepEstimator.Steps(321, estimated = false), estimator.update(status(0.2, 180, steps = 321)))
        assertEquals(StepEstimator.Steps(0, estimated = false), estimator.update(status(0.0, 0, steps = 0)))
    }

    @Test
    fun `estimates steps from distance at walking cadence`() {
        val estimator = StepEstimator()
        estimator.update(status(0.0, 0))
        val steps = estimator.update(status(1.0, 900)) // 1 km at 4 km/h
        assertTrue(steps.estimated)
        // At 4 km/h: 104 steps/min and 0.64 m per step, so about 1560 steps per km.
        assertTrue("got ${steps.count}", steps.count in 1500..1620)
    }

    @Test
    fun `starts from the distance already walked and restarts with a new session`() {
        val estimator = StepEstimator()
        val midSession = estimator.update(status(0.5, 450)).count
        assertTrue("got $midSession", midSession in 750..810)
        assertEquals(0L, estimator.update(status(0.0, 0)).count)
    }

    @Test
    fun `cadence grows with speed`() {
        assertEquals(93.5, StepEstimator.cadence(3.0), 0.001)
        assertEquals(125.0, StepEstimator.cadence(6.0), 0.001)
    }
}
