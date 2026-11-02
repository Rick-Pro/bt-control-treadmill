package io.github.rickpro.stride.session

import android.content.Context
import androidx.core.content.edit
import io.github.rickpro.stride.core.profile.Profile
import io.github.rickpro.stride.core.profile.Sex
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

    /**
     * The person's profile for calories and steps, or null when none is saved (or what is saved is not valid).
     * Kept in this app's private preferences only: backups are off and nothing is ever sent anywhere.
     */
    var profile: Profile?
        get() {
            val sex = prefs.getString(KEY_SEX, null)?.let { name -> Sex.entries.firstOrNull { it.name == name } } ?: return null
            val height = prefs.getString(KEY_HEIGHT_CM, null)?.toDoubleOrNull() ?: return null
            val weight = prefs.getString(KEY_WEIGHT_KG, null)?.toDoubleOrNull() ?: return null
            val age = if (prefs.contains(KEY_AGE)) prefs.getInt(KEY_AGE, 0) else null
            return Profile(sex, height, weight, age).takeIf { it.isValid }
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_SEX)
                remove(KEY_HEIGHT_CM)
                remove(KEY_WEIGHT_KG)
                remove(KEY_AGE)
            } else {
                putString(KEY_SEX, value.sex.name)
                putString(KEY_HEIGHT_CM, value.heightCm.toString())
                putString(KEY_WEIGHT_KG, value.weightKg.toString())
                if (value.ageYears != null) putInt(KEY_AGE, value.ageYears) else remove(KEY_AGE)
            }
        }

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
        private const val KEY_SEX = "profile_sex"
        private const val KEY_HEIGHT_CM = "profile_height_cm"
        private const val KEY_WEIGHT_KG = "profile_weight_kg"
        private const val KEY_AGE = "profile_age"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    }
}
