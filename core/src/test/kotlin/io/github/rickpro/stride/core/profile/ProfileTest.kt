package io.github.rickpro.stride.core.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {
    private val woman = Profile(Sex.FEMALE, heightCm = 170.0, weightKg = 62.0, ageYears = 34)
    private val man = Profile(Sex.MALE, heightCm = 180.0, weightKg = 82.0)

    @Test
    fun `step length is 0_413 of height for women and 0_415 for men`() {
        assertEquals(0.7021, woman.stepLengthM, 1e-9) // 0.413 x 1.70
        assertEquals(0.7470, man.stepLengthM, 1e-9) // 0.415 x 1.80
    }

    @Test
    fun `step length for other is the average of the two factors`() {
        val other = Profile(Sex.OTHER, heightCm = 175.0, weightKg = 70.0)
        assertEquals(0.414, Profile.STEP_FACTOR_OTHER, 1e-12)
        assertEquals(0.7245, other.stepLengthM, 1e-9)
        assertTrue(other.stepLengthM in minOf(woman.copy(heightCm = 175.0).stepLengthM, man.copy(heightCm = 175.0).stepLengthM)..
            maxOf(woman.copy(heightCm = 175.0).stepLengthM, man.copy(heightCm = 175.0).stepLengthM))
    }

    @Test
    fun `a taller person takes longer steps`() {
        assertTrue(woman.copy(heightCm = 180.0).stepLengthM > woman.stepLengthM)
    }

    @Test
    fun `weight byte is whole kilograms clamped to 1 to 255`() {
        assertEquals(62, woman.weightByte)
        assertEquals(71, woman.copy(weightKg = 70.6).weightByte)
        assertEquals(70, woman.copy(weightKg = 70.4).weightByte)
        assertEquals(1, woman.copy(weightKg = 0.2).weightByte)
        assertEquals(255, woman.copy(weightKg = 255.4).weightByte)
        assertEquals(255, woman.copy(weightKg = 400.0).weightByte)
    }

    @Test
    fun `ranges decide what is a valid profile`() {
        assertTrue(woman.isValid)
        assertTrue(man.isValid) // age is optional
        assertFalse(woman.copy(heightCm = 99.0).isValid)
        assertFalse(woman.copy(heightCm = 251.0).isValid)
        assertFalse(woman.copy(weightKg = 19.0).isValid)
        assertFalse(woman.copy(weightKg = 301.0).isValid)
        assertFalse(woman.copy(ageYears = 4).isValid)
        assertFalse(woman.copy(ageYears = 121).isValid)
        assertTrue(woman.copy(heightCm = 100.0, weightKg = 20.0, ageYears = 5).isValid) // the edges are in
        assertTrue(woman.copy(heightCm = 250.0, weightKg = 300.0, ageYears = 120).isValid)
        assertFalse(woman.copy(heightCm = Double.NaN).isValid)
    }
}
