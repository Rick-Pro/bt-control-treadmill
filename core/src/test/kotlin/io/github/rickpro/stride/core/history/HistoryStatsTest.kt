package io.github.rickpro.stride.core.history

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryStatsTest {
    // Pacific Standard Time, UTC-8.
    private val pst = -8 * 3600
    private val dayMs = 24L * 3600 * 1000

    // 2026-11-01 (a Sunday) at 20:18 PST = 2026-11-02 04:18 UTC.
    private val sundayEvening = 1_793_593_080_000L

    private fun walk(startedAt: Long, seconds: Long = 600, km: Double = 0.5, kcal: Int = 50) = Workout(
        id = Workout.idFor(startedAt), startedAtEpochMs = startedAt, endedAtEpochMs = startedAt + seconds * 1000,
        durationSeconds = seconds, distanceKm = km, kcal = kcal, steps = 0, stepsEstimated = true,
        avgSpeedKmh = km / (seconds / 3600.0), maxSpeedKmh = 3.0, samples = emptyList(),
    )

    @Test
    fun `totals add up`() {
        val totals = HistoryStats.totals(listOf(walk(1, 600, 0.5, 50), walk(2, 300, 0.25, 20)))
        assertEquals(HistoryStats.Totals(2, 900, 0.75, 70), totals)
        assertEquals(HistoryStats.Totals(0, 0, 0.0, 0), HistoryStats.totals(emptyList()))
    }

    @Test
    fun `local day follows the zone offset, not UTC`() {
        // 04:18 UTC on Nov 2 is still Nov 1 in PST.
        val utcDay = HistoryStats.localDay(sundayEvening, 0)
        assertEquals(utcDay - 1, HistoryStats.localDay(sundayEvening, pst))
    }

    @Test
    fun `this week runs Monday to Sunday in local time`() {
        val sixDaysAgo = sundayEvening - 6 * dayMs // Monday evening: inside the week
        val sevenDaysAgo = sundayEvening - 7 * dayMs // last Sunday: outside
        val walks = listOf(walk(sundayEvening), walk(sixDaysAgo), walk(sevenDaysAgo))
        assertEquals(2, HistoryStats.thisWeek(walks, sundayEvening, pst).walks)
        assertEquals(1, HistoryStats.today(walks, sundayEvening, pst).walks)
    }

    @Test
    fun `streak counts consecutive days and survives one missed day only until tomorrow`() {
        val walks = listOf(walk(sundayEvening), walk(sundayEvening - dayMs), walk(sundayEvening - 2 * dayMs), walk(sundayEvening - 4 * dayMs))
        assertEquals(3, HistoryStats.streakDays(walks, sundayEvening, pst))
        // Tomorrow with no walk yet: the streak still stands.
        assertEquals(3, HistoryStats.streakDays(walks, sundayEvening + dayMs, pst))
        // Two days later: gone.
        assertEquals(0, HistoryStats.streakDays(walks, sundayEvening + 2 * dayMs, pst))
        assertEquals(0, HistoryStats.streakDays(emptyList(), sundayEvening, pst))
    }

    @Test
    fun `totals use the estimate where there is one and the pad's number where there is not`() {
        val estimated = walk(1, 600, 0.5, kcal = 0).copy(estimatedKcal = 40)
        val padOnly = walk(2, 300, 0.25, kcal = 20)
        assertEquals(60, HistoryStats.totals(listOf(estimated, padOnly)).kcal)
    }
}
