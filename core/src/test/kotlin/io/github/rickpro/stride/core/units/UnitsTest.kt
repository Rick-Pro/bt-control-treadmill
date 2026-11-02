package io.github.rickpro.stride.core.units

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test
    fun `converts between km and miles both ways`() {
        assertEquals(0.621, Units.kmToMi(1.0), 0.001)
        assertEquals(1.0, Units.mphToKmh(Units.kmhToMph(1.0)), 1e-9)
        assertEquals(3.728, Units.kmhToMph(6.0), 0.001)
    }

    @Test
    fun `speed text has one decimal in the chosen unit`() {
        assertEquals("1.0", Units.speedText(1.0, MeasureUnit.METRIC))
        assertEquals("0.6", Units.speedText(1.0, MeasureUnit.IMPERIAL)) // what the PitPat app shows at 1.0 km/h
        assertEquals("2.5", Units.speedText(2.5, MeasureUnit.METRIC))
        assertEquals("1.6", Units.speedText(2.5, MeasureUnit.IMPERIAL))
        assertEquals("mph", Units.speedUnitLabel(MeasureUnit.IMPERIAL))
        assertEquals("km/h", Units.speedUnitLabel(MeasureUnit.METRIC))
    }

    @Test
    fun `distance text has two decimals`() {
        assertEquals("0.93", Units.distanceText(0.93, MeasureUnit.METRIC))
        assertEquals("0.58", Units.distanceText(0.93, MeasureUnit.IMPERIAL))
        assertEquals("mi", Units.distanceUnitLabel(MeasureUnit.IMPERIAL))
        assertEquals("km", Units.distanceUnitLabel(MeasureUnit.METRIC))
    }

    @Test
    fun `duration text drops the hour until it is needed`() {
        assertEquals("0:00", Units.durationText(0))
        assertEquals("0:28", Units.durationText(28))
        assertEquals("45:33", Units.durationText(45 * 60 + 33))
        assertEquals("1:02:07", Units.durationText(3600 + 2 * 60 + 7))
        assertEquals("0:00", Units.durationText(-5))
    }

    @Test
    fun `pace is minutes per unit and dashes when stopped`() {
        assertEquals("--", Units.paceText(0.0, MeasureUnit.METRIC))
        assertEquals("20'00\"", Units.paceText(3.0, MeasureUnit.METRIC))
        assertEquals("32'11\"", Units.paceText(3.0, MeasureUnit.IMPERIAL))
        assertEquals("10'00\"", Units.paceText(6.0, MeasureUnit.METRIC))
    }

    @Test
    fun `converts height between cm and feet and inches`() {
        assertEquals(69.0, Units.cmToInches(175.26), 1e-9)
        assertEquals(175.26, Units.feetAndInchesToCm(5, 9.0), 1e-9)
        assertEquals(5 to 9, Units.cmToFeetAndInches(175.26))
        assertEquals(6 to 0, Units.cmToFeetAndInches(182.88))
        assertEquals(5 to 10, Units.cmToFeetAndInches(177.8)) // 70 in
        assertEquals(6 to 0, Units.cmToFeetAndInches(181.8)) // 71.57 in rounds up and carries into the next foot
        assertEquals(5 to 0, Units.cmToFeetAndInches(152.4))
        assertEquals(170.0, Units.feetAndInchesToCm(5, Units.cmToInches(170.0) - 60), 1e-9)
    }

    @Test
    fun `converts weight between kg and pounds both ways`() {
        assertEquals(154.3236, Units.kgToLb(70.0), 1e-4)
        assertEquals(69.8532, Units.lbToKg(154.0), 1e-4)
        assertEquals(70.0, Units.lbToKg(Units.kgToLb(70.0)), 1e-9)
    }
}
