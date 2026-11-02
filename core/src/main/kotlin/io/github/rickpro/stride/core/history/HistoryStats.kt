package io.github.rickpro.stride.core.history

/** Sums and streaks over the saved walks. Days are counted in the user's time zone, given as an offset. */
object HistoryStats {
    data class Totals(val walks: Int, val seconds: Long, val km: Double, val kcal: Int)

    private const val DAY_MS = 24L * 3600 * 1000
    private const val WEEK_DAYS = 7

    fun totals(workouts: List<Workout>): Totals = Totals(
        walks = workouts.size,
        seconds = workouts.sumOf { it.durationSeconds },
        km = workouts.sumOf { it.distanceKm },
        kcal = workouts.sumOf { it.kcal },
    )

    /** Walks that started in the current Monday-to-Sunday week, local time. */
    fun thisWeek(workouts: List<Workout>, nowEpochMs: Long, zoneOffsetSeconds: Int): Totals {
        val today = localDay(nowEpochMs, zoneOffsetSeconds)
        val monday = today - mondayBasedWeekday(today)
        return totals(workouts.filter { localDay(it.startedAtEpochMs, zoneOffsetSeconds) >= monday })
    }

    /** Walks that started today, local time. */
    fun today(workouts: List<Workout>, nowEpochMs: Long, zoneOffsetSeconds: Int): Totals {
        val today = localDay(nowEpochMs, zoneOffsetSeconds)
        return totals(workouts.filter { localDay(it.startedAtEpochMs, zoneOffsetSeconds) == today })
    }

    /**
     * Consecutive days with at least one walk, ending today or yesterday (a streak survives until the day
     * after a missed day). 0 when there is no walk today or yesterday.
     */
    fun streakDays(workouts: List<Workout>, nowEpochMs: Long, zoneOffsetSeconds: Int): Int {
        if (workouts.isEmpty()) return 0
        val days = workouts.map { localDay(it.startedAtEpochMs, zoneOffsetSeconds) }.toHashSet()
        val today = localDay(nowEpochMs, zoneOffsetSeconds)
        var day = when {
            today in days -> today
            (today - 1) in days -> today - 1
            else -> return 0
        }
        var streak = 0
        while (day in days) {
            streak++
            day--
        }
        return streak
    }

    /** Days since the epoch in local time; the same number for every instant of one local day. */
    fun localDay(epochMs: Long, zoneOffsetSeconds: Int): Long =
        Math.floorDiv(epochMs + zoneOffsetSeconds * 1000L, DAY_MS)

    /** 0 for Monday up to 6 for Sunday. Day 0 (1970-01-01) was a Thursday. */
    private fun mondayBasedWeekday(localDay: Long): Long = Math.floorMod(localDay + 3, WEEK_DAYS.toLong())
}
