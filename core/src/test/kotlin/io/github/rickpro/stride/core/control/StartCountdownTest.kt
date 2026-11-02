package io.github.rickpro.stride.core.control

import io.github.rickpro.stride.core.protocol.RunState
import io.github.rickpro.stride.core.protocol.TestFrames
import io.github.rickpro.stride.core.protocol.PitPatProtocol
import io.github.rickpro.stride.core.protocol.TreadmillStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartCountdownTest {
    private val countdown = StartCountdown()

    @Test
    fun `measures how long STARTING lasted, as in the build 3 log`() {
        // Start at 32.3 s, RUNNING at 35.4 s.
        assertNull(countdown.onStatus(RunState.STOPPED, 31_000))
        assertNull(countdown.onStatus(RunState.STARTING, 32_300))
        assertEquals(32_300L, countdown.startedAtMs)
        assertNull(countdown.onStatus(RunState.STARTING, 33_300))
        assertNull(countdown.onStatus(RunState.STARTING, 34_300))
        val ended = countdown.onStatus(RunState.RUNNING, 35_400)!!
        assertEquals(3_100L, ended.durationMs)
        assertEquals(RunState.RUNNING, ended.next)
        assertNull(countdown.startedAtMs)
    }

    @Test
    fun `repeated STARTING statuses do not restart the clock`() {
        countdown.onStatus(RunState.STARTING, 1_000)
        countdown.onStatus(RunState.STARTING, 2_000)
        countdown.onStatus(RunState.STARTING, 2_900)
        assertEquals(1_000L, countdown.startedAtMs)
    }

    @Test
    fun `a countdown cut short by STOP says what followed`() {
        countdown.onStatus(RunState.STARTING, 10_000)
        val ended = countdown.onStatus(RunState.STOPPED, 11_200)!!
        assertEquals(1_200L, ended.durationMs)
        assertEquals(RunState.STOPPED, ended.next)
    }

    @Test
    fun `each start has its own countdown`() {
        countdown.onStatus(RunState.STARTING, 1_000)
        assertEquals(3_000L, countdown.onStatus(RunState.RUNNING, 4_000)!!.durationMs)
        assertNull(countdown.onStatus(RunState.RUNNING, 5_000)) // nothing running, nothing to report
        countdown.onStatus(RunState.PAUSED, 6_000)
        countdown.onStatus(RunState.STARTING, 7_000) // resume from pause counts down again
        assertEquals(3_200L, countdown.onStatus(RunState.RUNNING, 10_200)!!.durationMs)
    }

    @Test
    fun `reset forgets a countdown when the link drops`() {
        countdown.onStatus(RunState.STARTING, 1_000)
        countdown.reset()
        assertNull(countdown.startedAtMs)
        assertNull(countdown.onStatus(RunState.RUNNING, 4_000)) // no stale duration after a reconnect
    }

    @Test
    fun `the count goes 3, 2, 1 and stays on 1`() {
        assertEquals(3, StartCountdown.digitAt(0))
        assertEquals(3, StartCountdown.digitAt(999))
        assertEquals(2, StartCountdown.digitAt(1_000))
        assertEquals(2, StartCountdown.digitAt(1_999))
        assertEquals(1, StartCountdown.digitAt(2_000))
        assertEquals(1, StartCountdown.digitAt(2_999))
        assertEquals(1, StartCountdown.digitAt(3_100)) // the pad took 3.1 s: never 0
        assertEquals(1, StartCountdown.digitAt(60_000))
        assertEquals(3, StartCountdown.digitAt(-500)) // a clock that stepped back
    }

    @Test
    fun `the pad reports STOPPED at once while the belt is still slowing down`() {
        fun status(flags: Int, speedRaw: Int): TreadmillStatus =
            PitPatProtocol.parseStatus(TestFrames.status(flags = flags, speed = speedRaw, target = 0))!!
        // Build 3: after STOP the state is STOPPED while the speed ramps from 1.9 km/h to 0.
        assertTrue(status(0x82, 1_900).isStopping)
        assertTrue(status(0x82, 400).isStopping)
        assertFalse(status(0x82, 50).isStopping) // 0.05 km/h reads as 0.0 on the pad
        assertFalse(status(0x82, 0).isStopping)
        assertFalse(status(0x8A, 1_900).isStopping) // running is not stopping
        assertFalse(status(0x92, 0).isStopping) // paused
        assertFalse(status(0x9A, 0).isStopping)
    }
}
