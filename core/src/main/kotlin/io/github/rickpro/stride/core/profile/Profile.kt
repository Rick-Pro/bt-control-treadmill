package io.github.rickpro.stride.core.profile

import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE, OTHER }

/**
 * The few things Stride may know about the person walking: sex, height, weight and, optionally, age. They stay on
 * the phone (SharedPreferences) and are used for three things only: a calorie estimate, the step length for the
 * step estimate, and the weight byte sent to the pad so its own calories improve.
 *
 * Height and weight are always kept in cm and kg; the screen converts to feet, inches and pounds.
 */
data class Profile(
    val sex: Sex,
    val heightCm: Double,
    val weightKg: Double,
    val ageYears: Int? = null,
) {
    /** True when every value is inside a range a person could really have. */
    val isValid: Boolean
        get() = heightCm in HEIGHT_CM && weightKg in WEIGHT_KG && (ageYears == null || ageYears in AGE_YEARS)

    /** The weight byte of a control frame: whole kilograms, 1 to 255. */
    val weightByte: Int get() = weightKg.roundToInt().coerceIn(MIN_WEIGHT_BYTE, MAX_WEIGHT_BYTE)

    /** Walking step length in metres, from height: 0.413 x height for women, 0.415 x for men, in between for other. */
    val stepLengthM: Double get() = heightCm / 100.0 * stepLengthFactor(sex)

    companion object {
        val HEIGHT_CM = 100.0..250.0
        val WEIGHT_KG = 20.0..300.0
        val AGE_YEARS = 5..120
        const val MIN_WEIGHT_BYTE = 1
        const val MAX_WEIGHT_BYTE = 255

        const val STEP_FACTOR_FEMALE = 0.413
        const val STEP_FACTOR_MALE = 0.415

        /** For "other" the two published factors are averaged. */
        const val STEP_FACTOR_OTHER = (STEP_FACTOR_FEMALE + STEP_FACTOR_MALE) / 2

        fun stepLengthFactor(sex: Sex): Double = when (sex) {
            Sex.FEMALE -> STEP_FACTOR_FEMALE
            Sex.MALE -> STEP_FACTOR_MALE
            Sex.OTHER -> STEP_FACTOR_OTHER
        }
    }
}
