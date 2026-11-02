package io.github.rickpro.stride.session

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The one vibration Stride uses: a lost link, or a stop the treadmill did not confirm. Needs VIBRATE. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    /** Long-short-long pattern. */
    fun alert() {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 150, 500, 150, 500), -1))
    }
}
