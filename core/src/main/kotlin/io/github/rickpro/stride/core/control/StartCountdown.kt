package io.github.rickpro.stride.core.control

import io.github.rickpro.stride.core.protocol.RunState

/**
 * Follows the pad's start countdown. When Start is pressed the pad beeps and reports `STARTING` for about three
 * seconds before the belt moves (build 3 log: STARTING at 32.3 s, RUNNING at 35.4 s). This tracks when that began, so
 * the screen can show a 3-2-1 count, and measures how long it really lasted, so the log can say.
 *
 * Feed it every status with a monotonic clock. Not thread safe: use it from the Bluetooth thread.
 */
class StartCountdown {

    /** The countdown ended: how long `STARTING` lasted and the state that followed it. */
    data class Ended(val durationMs: Long, val next: RunState)

    /** When the current countdown began, or null when none is running. */
    var startedAtMs: Long? = null
        private set

    /** Returns [Ended] on the status that ends a countdown, otherwise null. */
    fun onStatus(state: RunState, nowMs: Long): Ended? {
        val began = startedAtMs
        return when {
            state == RunState.STARTING -> {
                if (began == null) startedAtMs = nowMs
                null
            }
            began != null -> {
                startedAtMs = null
                Ended(durationMs = (nowMs - began).coerceAtLeast(0), next = state)
            }
            else -> null
        }
    }

    /** The link dropped or was closed: whatever was counting is not known any more. */
    fun reset() {
        startedAtMs = null
    }

    companion object {
        /** About how long the pad counts down. Only used to pace the on-screen count. */
        const val EXPECTED_MS = 3_000L

        /**
         * The digit to show [elapsedMs] after the countdown began: 3, then 2, then 1. It stays on 1 if the pad takes
         * longer than expected, and never shows 0, because the screen changes when the pad says RUNNING.
         */
        fun digitAt(elapsedMs: Long): Int {
            val secondsGone = (elapsedMs.coerceAtLeast(0) / 1_000L).toInt()
            return (EXPECTED_MS / 1_000L).toInt().minus(secondsGone).coerceIn(1, 3)
        }
    }
}
