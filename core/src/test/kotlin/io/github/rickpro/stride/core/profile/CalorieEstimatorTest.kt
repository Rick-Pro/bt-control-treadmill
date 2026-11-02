package io.github.rickpro.stride.core.profile

import io.github.rickpro.stride.core.protocol.PitPatProtocol
import io.github.rickpro.stride.core.protocol.TestFrames
import io.github.rickpro.stride.core.protocol.TreadmillStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieEstimatorTest {
    private val me = Profile(Sex.MALE, heightCm = 175.0, weightKg = 70.0)
    private val estimator = CalorieEstimator()

    /** A RUNNING status [seconds] into a walk at a steady [kmh]; flags 8A, duration in ms as the pad sends it. */
    private fun running(seconds: Long, kmh: Double, flags: Int = 0x8A, distanceM: Long? = null): TreadmillStatus =
        PitPatProtocol.parseStatus(
            TestFrames.status(
                flags = flags, speed = (kmh * 1000).toInt(), target = (kmh * 1000).toInt(),
                distanceM = distanceM ?: (kmh * 1000 * seconds / 3600).toLong(), durationRaw = seconds * 1000, steps = 0, kcal = 0,
            ),
        )!!

    // ------------------------------------------------------------------ the formula

    @Test
    fun `MET follows the ACSM walking equation at level grade`() {
        assertEquals(1.0, WalkingEnergy.met(0.0), 1e-9) // resting
        assertEquals(1.4762, WalkingEnergy.met(1.0), 1e-4)
        assertEquals(2.5238, WalkingEnergy.met(3.2), 1e-4) // 2 mph
        assertEquals(3.2857, WalkingEnergy.met(4.8), 1e-4) // 3 mph
        assertEquals(3.8571, WalkingEnergy.met(6.0), 1e-4)
        assertEquals(4.0476, WalkingEnergy.met(6.4), 1e-4) // 4 mph
        assertEquals(1.0, WalkingEnergy.met(-2.0), 1e-9) // never below resting
    }

    @Test
    fun `kcal is MET times kilograms times hours`() {
        assertEquals(230.0, WalkingEnergy.kcal(70.0, 4.8, 3_600.0), 1e-6)
        assertEquals(38.3333, WalkingEnergy.kcal(70.0, 4.8, 600.0), 1e-3)
        assertEquals(0.0, WalkingEnergy.kcal(70.0, 4.8, -5.0), 1e-9)
        assertTrue(WalkingEnergy.kcal(90.0, 4.8, 600.0) > WalkingEnergy.kcal(60.0, 4.8, 600.0)) // heavier burns more
        assertTrue(WalkingEnergy.kcal(70.0, 6.0, 600.0) > WalkingEnergy.kcal(70.0, 3.0, 600.0)) // faster burns more
    }

    // ------------------------------------------------------------------ adding up a walk

    @Test
    fun `no profile means no estimate`() {
        assertNull(estimator.update(running(0, 0.0), null))
        assertNull(estimator.update(running(60, 3.0), null))
    }

    @Test
    fun `a steady walk adds up to MET x kg x hours`() {
        var kcal: Int? = null
        for (s in 0L..600L) kcal = estimator.update(running(s, 4.8), me)
        assertEquals(38, kcal) // 38.33 kcal for 10 minutes at 4.8 km/h and 70 kg
    }

    @Test
    fun `pausing costs nothing because the pad's duration counter stands still`() {
        for (s in 0L..300L) estimator.update(running(s, 4.8), me)
        val before = estimator.update(running(300, 4.8), me)!!
        repeat(120) { estimator.update(running(300, 0.0, flags = 0x92, distanceM = 400), me) } // two minutes paused
        assertEquals(before, estimator.update(running(300, 0.0, flags = 0x92, distanceM = 400), me))
        val resumed = estimator.update(running(301, 4.8, distanceM = 401), me)!!
        assertTrue(resumed >= before)
        assertTrue(resumed - before <= 1)
    }

    @Test
    fun `joining a walk late catches up from the average speed`() {
        // Connected 10 minutes in: 0.8 km in 600 s is 4.8 km/h.
        val caughtUp = estimator.update(running(600, 4.8, distanceM = 800), me)
        assertEquals(38, caughtUp)
        assertEquals(38, estimator.update(running(600, 4.8, distanceM = 800), me)) // same status again adds nothing
    }

    @Test
    fun `a walk that starts over resets the count`() {
        for (s in 0L..600L) estimator.update(running(s, 4.8), me)
        val next = estimator.update(running(2, 1.6, distanceM = 1), me)!!
        assertTrue("got $next", next <= 1)
    }

    @Test
    fun `a gap in the data is integrated from the distance walked`() {
        estimator.update(running(0, 3.0, distanceM = 0), me)
        // Nothing for 100 s, then a status: 0.133 km in 100 s is 4.8 km/h, whatever the two speeds seen say.
        val after = estimator.update(running(100, 3.0, distanceM = 133), me)
        assertEquals(Math.round(WalkingEnergy.kcal(70.0, 133.0 / 1000 / (100 / 3600.0), 100.0)).toInt(), after)
    }

    @Test
    fun `a weight change applies from then on`() {
        for (s in 0L..300L) estimator.update(running(s, 4.8), me)
        val before = estimator.update(running(300, 4.8), me)!!
        var after: Int? = null
        for (s in 301L..600L) after = estimator.update(running(s, 4.8), me.copy(weightKg = 140.0))
        // The first 300 s at 70 kg stay; the next 300 s count double.
        val expected = before + WalkingEnergy.kcal(140.0, 4.8, 300.0)
        assertEquals(expected, after!!.toDouble(), 1.5)
    }

    @Test
    fun `removing the profile forgets the walk, and adding one catches up again`() {
        for (s in 0L..300L) estimator.update(running(s, 4.8), me)
        assertNull(estimator.update(running(301, 4.8), null))
        assertEquals(19, estimator.update(running(300, 4.8, distanceM = 400), me)) // 5 min at 4.8 km/h, 70 kg
    }

    @Test
    fun `reset starts a new walk`() {
        for (s in 0L..300L) estimator.update(running(s, 4.8), me)
        estimator.reset()
        assertEquals(0, estimator.update(running(0, 0.0), me))
    }
}
