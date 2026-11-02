package io.github.rickpro.stride.core.profile

import io.github.rickpro.stride.core.units.MeasureUnit
import io.github.rickpro.stride.core.units.Units
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the "Me" form in Settings shows and how it turns what was typed back into a [Profile]. Height and weight
 * are typed in the chosen unit system (cm and kg, or feet, inches and pounds) and always stored in cm and kg.
 */
object ProfileForm {
    /** The text of every field. [heightMajor] is cm, or feet; [heightMinor] is inches and only used in imperial. */
    data class Fields(
        val sex: Sex? = null,
        val heightMajor: String = "",
        val heightMinor: String = "",
        val weight: String = "",
        val age: String = "",
    )

    fun fieldsFor(profile: Profile?, unit: MeasureUnit): Fields {
        profile ?: return Fields()
        val age = profile.ageYears?.toString().orEmpty()
        return if (unit == MeasureUnit.IMPERIAL) {
            val (feet, inches) = Units.cmToFeetAndInches(profile.heightCm)
            Fields(profile.sex, feet.toString(), inches.toString(), trimmed(Units.kgToLb(profile.weightKg)), age)
        } else {
            Fields(profile.sex, profile.heightCm.roundToInt().toString(), "", trimmed(profile.weightKg), age)
        }
    }

    /** The profile the fields describe, or null while anything is missing, not a number, or out of range. */
    fun parse(fields: Fields, unit: MeasureUnit): Profile? {
        val sex = fields.sex ?: return null
        val heightCm = if (unit == MeasureUnit.IMPERIAL) {
            val feet = number(fields.heightMajor)?.takeIf { it >= 0 && it == Math.floor(it) } ?: return null
            val inches = if (fields.heightMinor.isBlank()) 0.0 else number(fields.heightMinor) ?: return null
            if (inches < 0) return null
            Units.feetAndInchesToCm(feet.toInt(), inches)
        } else {
            number(fields.heightMajor) ?: return null
        }
        val weight = number(fields.weight) ?: return null
        val weightKg = if (unit == MeasureUnit.IMPERIAL) Units.lbToKg(weight) else weight
        val age = if (fields.age.isBlank()) null else fields.age.trim().toIntOrNull() ?: return null
        return Profile(sex, heightCm, weightKg, age).takeIf { it.isValid }
    }

    /** A decimal typed with a dot or a comma. Null for blank, NaN or anything else. */
    private fun number(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }

    private fun trimmed(value: Double): String =
        String.format(Locale.ROOT, "%.1f", value).trimEnd('0').trimEnd('.')
}
