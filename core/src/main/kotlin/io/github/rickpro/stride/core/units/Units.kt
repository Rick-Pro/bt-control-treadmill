package io.github.rickpro.stride.core.units

import java.util.Locale
import kotlin.math.roundToInt

/** The unit system the user sees. The protocol itself always speaks km/h and km. */
enum class MeasureUnit { METRIC, IMPERIAL }

/** Conversion and text formatting for everything the screen shows. No locale surprises: always a dot. */
object Units {
    const val KM_PER_MILE = 1.609344

    fun kmToMi(km: Double): Double = km / KM_PER_MILE

    fun kmhToMph(kmh: Double): Double = kmh / KM_PER_MILE

    fun mphToKmh(mph: Double): Double = mph * KM_PER_MILE

    /** Speed with one decimal in the chosen unit, for example "2.5". */
    fun speedText(kmh: Double, unit: MeasureUnit): String = oneDecimal(if (unit == MeasureUnit.IMPERIAL) kmhToMph(kmh) else kmh)

    fun speedUnitLabel(unit: MeasureUnit): String = if (unit == MeasureUnit.IMPERIAL) "mph" else "km/h"

    /** Distance with two decimals in the chosen unit, for example "0.58". */
    fun distanceText(km: Double, unit: MeasureUnit): String =
        String.format(Locale.ROOT, "%.2f", if (unit == MeasureUnit.IMPERIAL) kmToMi(km) else km)

    fun distanceUnitLabel(unit: MeasureUnit): String = if (unit == MeasureUnit.IMPERIAL) "mi" else "km"

    /** "1:02:07" with hours, or "45:33" under an hour. Negative values show as 0:00. */
    fun durationText(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** Minutes and seconds per mile or per km, for example "24'00\"". "--" when the belt is not moving. */
    fun paceText(kmh: Double, unit: MeasureUnit): String {
        if (kmh <= 0.0) return "--"
        val perUnitHours = if (unit == MeasureUnit.IMPERIAL) KM_PER_MILE / kmh else 1.0 / kmh
        val totalSeconds = (perUnitHours * 3600).roundToInt()
        return String.format(Locale.ROOT, "%d'%02d\"", totalSeconds / 60, totalSeconds % 60)
    }

    private fun oneDecimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
}
