package io.github.rickpro.stride.core.profile

import io.github.rickpro.stride.core.protocol.TreadmillStatus
import kotlin.math.roundToInt

/**
 * Energy of level walking on a treadmill.
 *
 * MET comes from speed with the ACSM walking equation at 0% grade: oxygen use is 3.5 + 0.1 x speed (m/min) in
 * ml/kg/min, and one MET is 3.5, so MET = 1 + speed (m/min) / 35. That gives about 2.5 MET at 3.2 km/h, 3.3 at
 * 4.8 km/h and 4.1 at 6.4 km/h. The equation is meant for 3 to 6 km/h and the pad runs 1 to 6; below 3 km/h it is
 * a plain straight-line estimate. Then kcal = MET x weight (kg) x hours, which counts resting energy too, like a
 * fitness tracker does.
 *
 * It is an estimate for one person's profile, not a measurement, and the screen says so.
 */
object WalkingEnergy {
    fun met(speedKmh: Double): Double {
        val metersPerMinute = speedKmh.coerceAtLeast(0.0) * 1000.0 / 60.0
        return 1.0 + metersPerMinute / 35.0
    }

    fun kcal(weightKg: Double, speedKmh: Double, seconds: Double): Double =
        met(speedKmh) * weightKg * (seconds.coerceAtLeast(0.0) / 3600.0)
}

/**
 * Adds up the calories of one walk from the pad's own counters, so pauses cost nothing (the duration counter stands
 * still) and a walk the app joined late is caught up from its average speed. Feed it every status. Without a profile
 * it returns null and forgets everything, so adding a profile in the middle of a walk starts from a caught-up value.
 */
class CalorieEstimator {
    private var lastDurationS: Long? = null
    private var lastDistanceKm = 0.0
    private var lastSpeedKmh = 0.0
    private var kcal = 0.0

    /** The walk's estimated calories so far, whole kcal, or null when there is no profile. */
    fun update(status: TreadmillStatus, profile: Profile?): Int? {
        if (profile == null) {
            reset()
            return null
        }
        val last = lastDurationS
        val duration = status.durationSeconds
        if (last == null || duration < last || status.distanceKm < lastDistanceKm) {
            // First status, or the pad's counters started over: catch up from the walk's average speed.
            kcal = if (duration > 0) WalkingEnergy.kcal(profile.weightKg, averageSpeed(status), duration.toDouble()) else 0.0
        } else {
            val seconds = duration - last
            if (seconds > 0) {
                val speed = if (seconds <= MAX_STEP_S) {
                    (lastSpeedKmh + status.speedKmh) / 2.0
                } else {
                    // Data was missing for a while: use the distance walked over the gap instead.
                    (status.distanceKm - lastDistanceKm) / (seconds / 3600.0)
                }
                kcal += WalkingEnergy.kcal(profile.weightKg, speed, seconds.toDouble())
            }
        }
        lastDurationS = duration
        lastDistanceKm = status.distanceKm
        lastSpeedKmh = status.speedKmh
        return kcal.roundToInt()
    }

    fun reset() {
        lastDurationS = null
        lastDistanceKm = 0.0
        lastSpeedKmh = 0.0
        kcal = 0.0
    }

    private fun averageSpeed(status: TreadmillStatus): Double =
        if (status.durationSeconds > 0) status.distanceKm / (status.durationSeconds / 3600.0) else 0.0

    private companion object {
        /** Longest gap between two statuses that is still integrated with the two speeds seen. */
        const val MAX_STEP_S = 30L
    }
}
