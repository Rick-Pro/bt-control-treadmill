package io.github.rickpro.stride.core.history

/** One point of the speed chart: belt time in seconds and the speed then. */
data class SpeedSample(val atSeconds: Long, val kmh: Double)

/** One finished walk, as the treadmill counted it. Stored one per line by [WorkoutJson]. */
data class Workout(
    /** "w-" followed by [startedAtEpochMs]. */
    val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    /** From the treadmill's own counter at the end, so pauses are not counted. */
    val durationSeconds: Long,
    val distanceKm: Double,
    /** The pad's own calorie count. Always kept, even when it is 0 (Ricardo's pad never fills it in). */
    val kcal: Int,
    val steps: Long,
    /** True when the treadmill reported no steps and they were estimated from distance. */
    val stepsEstimated: Boolean,
    /** Distance over belt time. */
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    /** One sample per 5 s of belt time, at most 2000, for the chart. */
    val samples: List<SpeedSample>,
    /** Stride's own estimate from the person's profile, or null when there was no profile during the walk. */
    val estimatedKcal: Int? = null,
) {
    /** What to show as the walk's calories: the estimate when there is one, else the pad's number. */
    val bestKcal: Int get() = estimatedKcal ?: kcal

    companion object {
        const val MAX_SAMPLES = 2000
        const val SAMPLE_EVERY_SECONDS = 5L

        fun idFor(startedAtEpochMs: Long): String = "w-$startedAtEpochMs"
    }
}
