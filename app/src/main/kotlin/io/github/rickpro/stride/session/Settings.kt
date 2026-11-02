package io.github.rickpro.stride.session

import android.content.Context
import androidx.core.content.edit
import io.github.rickpro.stride.core.units.MeasureUnit

/** Small persistent settings: the remembered treadmill, units, presets and options. Nothing leaves the phone. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val deviceAddress: String? get() = prefs.getString(KEY_ADDRESS, null)
    val deviceName: String? get() = prefs.getString(KEY_NAME, null)

    fun saveDevice(address: String, name: String?) = prefs.edit {
        putString(KEY_ADDRESS, address)
        putString(KEY_NAME, name)
    }

    fun forgetDevice() = prefs.edit {
        remove(KEY_ADDRESS)
        remove(KEY_NAME)
    }

    /** Speed for the next start, in km/h (the protocol's unit). */
    var targetKmh: Double
        get() = prefs.getString(KEY_TARGET, null)?.toDoubleOrNull() ?: DEFAULT_TARGET_KMH
        set(value) {
            if (value != targetKmh) prefs.edit { putString(KEY_TARGET, value.toString()) }
        }

    var unit: MeasureUnit
        get() = if (prefs.getString(KEY_UNIT, null) == MeasureUnit.METRIC.name) MeasureUnit.METRIC else MeasureUnit.IMPERIAL
        set(value) = prefs.edit { putString(KEY_UNIT, value.name) }

    /** Four preset speeds in km/h. Defaults are 1.0, 1.5, 2.0 and 2.5 mph. */
    var presetsKmh: List<Double>
        get() = prefs.getString(KEY_PRESETS, null)
            ?.split(';')
            ?.mapNotNull { it.toDoubleOrNull() }
            ?.takeIf { it.size == DEFAULT_PRESETS_KMH.size }
            ?: DEFAULT_PRESETS_KMH
        set(value) = prefs.edit { putString(KEY_PRESETS, value.joinToString(";")) }

    var keepAliveEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ALIVE, true)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_ALIVE, value) }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, value) }

    companion object {
        val DEFAULT_PRESETS_KMH = listOf(1.6, 2.4, 3.2, 4.0)
        const val DEFAULT_TARGET_KMH = 2.0
        private const val KEY_ADDRESS = "device_address"
        private const val KEY_NAME = "device_name"
        private const val KEY_TARGET = "target_kmh"
        private const val KEY_UNIT = "unit"
        private const val KEY_PRESETS = "presets_kmh"
        private const val KEY_KEEP_ALIVE = "keep_alive"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    }
}
